#!/usr/bin/env python3
"""Create a labelled recursive Thumb disassembly for the recovered firmware."""

from __future__ import annotations

import argparse
import csv
import json
import struct
from collections import defaultdict, deque
from pathlib import Path

from capstone import CS_ARCH_ARM, CS_GRP_CALL, CS_GRP_JUMP, CS_GRP_RET, CS_MODE_LITTLE_ENDIAN, CS_MODE_THUMB, Cs
from capstone.arm import ARM_OP_IMM, ARM_OP_MEM, ARM_REG_LR, ARM_REG_PC


FLASH_BASE = 0x08000000


PERIPHERALS = {
    0x40000000: "TIM2", 0x40000400: "TIM3", 0x40000800: "TIM4", 0x40000C00: "TIM5",
    0x40001000: "TIM6", 0x40001400: "TIM7", 0x40002800: "RTC", 0x40002C00: "WWDG",
    0x40003000: "IWDG", 0x40003800: "SPI2", 0x40003C00: "SPI3", 0x40004400: "USART2",
    0x40004800: "USART3", 0x40004C00: "UART4", 0x40005000: "UART5", 0x40005400: "I2C1",
    0x40005800: "I2C2", 0x40006400: "CAN1", 0x40006C00: "BKP", 0x40007000: "PWR",
    0x40007400: "DAC", 0x40010000: "AFIO", 0x40010400: "EXTI", 0x40010800: "GPIOA",
    0x40010C00: "GPIOB", 0x40011000: "GPIOC", 0x40011400: "GPIOD", 0x40011800: "GPIOE",
    0x40011C00: "GPIOF", 0x40012000: "GPIOG", 0x40012400: "ADC1", 0x40012800: "ADC2",
    0x40012C00: "TIM1", 0x40013000: "SPI1", 0x40013400: "TIM8", 0x40013800: "USART1",
    0x40013C00: "ADC3", 0x40014C00: "TIM9", 0x40015000: "TIM10", 0x40015400: "TIM11",
    0x40018000: "SDIO", 0x40020000: "DMA1", 0x40020400: "DMA2", 0x40021000: "RCC",
    0x40022000: "FLASH", 0x40023000: "CRC", 0x60000000: "FSMC_BANK1", 0xE000E000: "SCS",
    0xE000E100: "NVIC", 0xE000ED00: "SCB", 0xE000E010: "SysTick", 0xE000E400: "NVIC_IPR",
}


KNOWN_NAMES = {
    0x08000130: "__main",
    0x08000234: "Reset_Handler",
    0x0800024E: "Default_Handler",
    0x08002C20: "SystemInit",
    0x08003464: "main",
    0x08002B08: "SVC_Handler",
    0x08002A8C: "PendSV_Handler",
    0x08002C1C: "SysTick_Handler",
    0x08001F98: "EXTI9_5_IRQHandler",
    0x08002D18: "TIM3_IRQHandler",
    0x08001F48: "EXTI15_10_IRQHandler",
    0x08001D14: "ADC_DMA_Init",
    0x08001F3A: "Key_EXTI_Init",
    0x080022E4: "FFT_Process_256",
    0x0800262C: "NVIC_PriorityGroupConfig",
    0x08002640: "OLED_Clear",
    0x08002870: "OLED_Init",
    0x08002928: "OLED_Set_Pos",
    0x08002C80: "TIM1_PWM_Init",
    0x08002DE8: "TIM3_Int_Init",
    0x08002F82: "TIM_SetCompare2",
    0x0800302C: "Application_Process",
    0x08003094: "OLED_WriteCommand",
    0x080030B0: "OLED_Refresh_Gram",
    0x08003234: "delay_init",
    0x08003278: "delay_ms",
    0x08003310: "OLED_GPIO_Init",
}


def read_u32(blob: bytes, address: int) -> int | None:
    offset = address - FLASH_BASE
    if offset < 0 or offset + 4 > len(blob):
        return None
    return struct.unpack_from("<I", blob, offset)[0]


def peripheral_name(value: int) -> str | None:
    candidates = [(base, name) for base, name in PERIPHERALS.items() if base <= value < base + 0x400]
    if not candidates:
        return None
    base, name = max(candidates)
    return f"{name}+0x{value - base:X}"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("binary", type=Path)
    parser.add_argument("analysis", type=Path)
    parser.add_argument("--output-dir", required=True, type=Path)
    args = parser.parse_args()

    blob = args.binary.read_bytes()
    end = FLASH_BASE + len(blob)
    analysis = json.loads(args.analysis.read_text(encoding="utf-8"))
    args.output_dir.mkdir(parents=True, exist_ok=True)

    md = Cs(CS_ARCH_ARM, CS_MODE_THUMB | CS_MODE_LITTLE_ENDIAN)
    md.detail = True

    names = dict(KNOWN_NAMES)
    seeds: set[int] = set(KNOWN_NAMES)
    for vector in analysis["vectors"][:76]:
        value = vector["value"] & ~1
        if FLASH_BASE <= value < end:
            seeds.add(value)
            if vector["index"] >= 16 and value != 0x0800024E:
                names.setdefault(value, f"IRQ{vector['index'] - 16}_Handler")

    decoded: dict[int, object] = {}
    owner: dict[int, int] = {}
    calls: set[tuple[int, int, int]] = set()
    literal_refs: list[tuple[int, int, int]] = []
    function_queue = deque(sorted(seeds))
    queued_functions = set(seeds)

    while function_queue:
        function = function_queue.popleft()
        blocks = deque([function])
        seen_blocks: set[int] = set()
        while blocks:
            address = blocks.popleft()
            if address in seen_blocks or not (FLASH_BASE <= address < end):
                continue
            seen_blocks.add(address)
            steps = 0
            while FLASH_BASE <= address < end and steps < 20000:
                steps += 1
                if address in owner and owner[address] != function:
                    break
                offset = address - FLASH_BASE
                insn = next(md.disasm(blob[offset : offset + 4], address, count=1), None)
                if insn is None:
                    break
                decoded[address] = insn
                owner.setdefault(address, function)

                # Resolve PC-relative literal loads used pervasively by ARMCC.
                if insn.mnemonic.startswith("ldr"):
                    for operand in insn.operands:
                        if operand.type == ARM_OP_MEM and operand.mem.base == ARM_REG_PC:
                            literal_address = ((insn.address + 4) & ~3) + operand.mem.disp
                            value = read_u32(blob, literal_address)
                            if value is not None:
                                literal_refs.append((insn.address, literal_address, value))

                direct_target = None
                for operand in insn.operands:
                    if operand.type == ARM_OP_IMM:
                        direct_target = operand.imm & ~1
                        break

                if insn.group(CS_GRP_CALL):
                    if direct_target is not None and FLASH_BASE <= direct_target < end:
                        calls.add((function, insn.address, direct_target))
                        if direct_target not in queued_functions:
                            queued_functions.add(direct_target)
                            function_queue.append(direct_target)
                    address += insn.size
                    continue

                mnemonic = insn.mnemonic
                is_return = insn.group(CS_GRP_RET)
                if mnemonic == "bx" and insn.operands and insn.operands[0].type != ARM_OP_IMM:
                    if insn.operands[0].reg == ARM_REG_LR:
                        is_return = True
                    else:
                        # Computed branch; continuing would decode an unrelated literal pool.
                        is_return = True
                if is_return:
                    break

                if insn.group(CS_GRP_JUMP) or mnemonic.startswith(("cbz", "cbnz")):
                    if direct_target is not None and FLASH_BASE <= direct_target < end:
                        blocks.append(direct_target)
                    if mnemonic in ("b", "b.w"):
                        break
                    address += insn.size
                    continue

                address += insn.size

    for address in sorted(queued_functions):
        names.setdefault(address, f"sub_{address:08X}")

    literal_by_instruction = defaultdict(list)
    for instruction, location, value in literal_refs:
        literal_by_instruction[instruction].append((location, value))

    with (args.output_dir / "minisbq_recursive.asm").open("w", encoding="utf-8", newline="\n") as out:
        out.write("; minisbq.hex recursive Thumb disassembly\n")
        out.write("; Hardware confirmed: STM32F103C8T6 (Cortex-M3, medium density)\n")
        out.write("; Note: the image itself uses an HD-style 60-IRQ vector table/generic SPL code.\n")
        out.write("; Names prefixed sub_ are inferred call targets, not original symbols.\n\n")
        last_owner = None
        previous_end = None
        for address in sorted(decoded):
            insn = decoded[address]
            current_owner = owner[address]
            if current_owner != last_owner or previous_end != address:
                if last_owner is not None:
                    out.write("\n")
                out.write(f"{names.get(current_owner, f'sub_{current_owner:08X}')}:\n")
                last_owner = current_owner
            elif address in names and address != current_owner:
                # A prior traversal can reach an adjacent/cross-shared routine first.
                # Preserve every discovered entry label even when ownership is ambiguous.
                out.write(f"\n{names[address]}:\n")
            annotation = ""
            if address in literal_by_instruction:
                parts = []
                for location, value in literal_by_instruction[address]:
                    description = peripheral_name(value)
                    if FLASH_BASE <= (value & ~1) < end:
                        description = names.get(value & ~1, "flash")
                    elif 0x20000000 <= value < 0x20010000:
                        description = "RAM"
                    parts.append(f"[0x{location:08X}]=0x{value:08X}" + (f" ({description})" if description else ""))
                annotation = " ; " + ", ".join(parts)
            elif insn.group(CS_GRP_CALL):
                target = next((op.imm & ~1 for op in insn.operands if op.type == ARM_OP_IMM), None)
                if target in names:
                    annotation = f" ; {names[target]}"
            out.write(f"  {insn.address:08X}: {insn.bytes.hex():<10} {insn.mnemonic:<9} {insn.op_str}{annotation}\n")
            previous_end = address + insn.size

    with (args.output_dir / "functions.csv").open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out)
        writer.writerow(["address", "name", "decoded_instruction_count"])
        counts = defaultdict(int)
        for address, function in owner.items():
            counts[function] += 1
        for function in sorted(queued_functions):
            writer.writerow([f"0x{function:08X}", names[function], counts[function]])

    with (args.output_dir / "calls.csv").open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out)
        writer.writerow(["caller_address", "caller_name", "call_site", "callee_address", "callee_name"])
        for caller, site, callee in sorted(calls):
            writer.writerow([f"0x{caller:08X}", names[caller], f"0x{site:08X}", f"0x{callee:08X}", names[callee]])

    useful_literals = sorted(set(literal_refs), key=lambda row: (row[2], row[0]))
    with (args.output_dir / "literal_references.csv").open("w", encoding="utf-8", newline="") as out:
        writer = csv.writer(out)
        writer.writerow(["instruction", "literal_location", "value", "classification"])
        for instruction, location, value in useful_literals:
            classification = peripheral_name(value) or ("RAM" if 0x20000000 <= value < 0x20010000 else "")
            if classification or FLASH_BASE <= (value & ~1) < end:
                writer.writerow([f"0x{instruction:08X}", f"0x{location:08X}", f"0x{value:08X}", classification or "FLASH"])

    coverage = sum(insn.size for insn in decoded.values())
    print(f"functions discovered: {len(queued_functions)}")
    print(f"instructions decoded: {len(decoded)}")
    print(f"decoded bytes: {coverage}/{len(blob)} ({coverage / len(blob):.1%})")
    print(f"direct calls: {len(calls)}")
    print(f"resolved literal references: {len(set(literal_refs))}")


if __name__ == "__main__":
    main()
