# `minisbq.hex` 固件抢救报告（第一轮）

## 结论

该镜像完整、可反汇编，并非加密或压缩固件。硬件型号现已由项目方确认为 **STM32F103C8T6**：Cortex-M3、LQFP48、官方 64 KiB Flash 和 20 KiB SRAM。固件功能上是带 128×64 单色 OLED、按键、ADC 采样和 256 点 FFT 的迷你示波器。

镜像本身使用了高密度版风格的 60 外部 IRQ 向量表，并链接了可识别 TIM8 等型号的通用 STM32F1 SPL 代码。STM32F103C8T6 属于中密度器件，重建工程应使用 `STM32F10X_MD` 和 `startup_stm32f10x_md.s`。当前实际启用的 EXTI9_5、TIM3、EXTI15_10 均属于 C8 支持的中断，固件大小和已观察到的 RAM 用量也在 C8 官方容量内，因此原固件仍可在该芯片上正常运行。

## 镜像取证

| 项目 | 结果 |
|---|---|
| 原始文件 | `E:\sbqfby\minisbq.hex` |
| SHA-256 | `7b84a1c367d6c114b77ade8b7556e9bebae53ea0019a7207f821e24f46b80bd8` |
| Intel HEX 校验 | 所有记录校验和正确，EOF/启动地址记录完整 |
| Flash 段 | `0x08000000–0x080071CB`，连续 29,132 字节，无地址洞 |
| 初始 MSP | `0x20003088` |
| Reset_Handler | 向量值 `0x08000235`，实际 Thumb 地址 `0x08000234` |
| ARMCC 启动入口 | `0x08000130` |
| main | `0x08003464` |
| 已确认 MCU | `STM32F103C8T6`，64 KiB Flash / 20 KiB SRAM / LQFP48 |
| 重建时器件宏 | `STM32F10X_MD` |
| 重建时存储布局 | IROM `0x08000000 + 0x10000`；IRAM `0x20000000 + 0x5000` |

向量表在 `0x08000130` 结束，共 16 个内核向量和 60 个外部 IRQ，形态符合 STM32F10x 高密度启动文件。这很可能是旧工程选用了 HD 启动文件，但它不会改变 C8 已有 IRQ 的编号。实际启用的主要外部中断是 EXTI9_5、TIM3 和 EXTI15_10，均在中密度 C8 的有效范围内。

## 已恢复的程序结构

| 地址 | 推定名称 | 依据 |
|---|---|---|
| `0x08003464` | `main` | ARMCC 运行库调用链及永久主循环 |
| `0x08003234` | `delay_init` | SysTick 和 SystemCoreClock/8 MHz 换算 |
| `0x08002DE8` | `TIM3_Int_Init` | TIM3 时基、NVIC IRQ 29、更新中断 |
| `0x08002C80` | `TIM1_PWM_Init` | PA9/TIM1 CH2、时基和输出比较配置 |
| `0x08002F82` | `TIM_SetCompare2` | 写 `TIMx->CCR2`（偏移 `0x38`） |
| `0x08002870` | `OLED_Init` | `AE 20 10 B0 C8 ... AF` OLED 控制器命令序列 |
| `0x08002640` | `OLED_Clear` | 清零 8×128 字节显存并刷新 |
| `0x08001D14` | `ADC_DMA_Init` | GPIOA 模拟输入、ADC1、DMA1 配置 |
| `0x08001F3A` | `Key_EXTI_Init` | GPIO/AFIO/EXTI 初始化 |
| `0x08002D18` | `TIM3_IRQHandler` | 256 点定时采样、最大/最小值和 FFT 输入组装 |
| `0x080022E4` | `FFT_Process_256` | 调用定点 FFT 后计算 256 个复数幅值 |
| `0x08000274` | 固定点 FFT 内核 | 256 点 Q15 复数蝶形运算和位反转 |
| `0x0800302C` | `Application_Process` | 采集完成后的波形/频谱/UI 状态调度 |

主函数按顺序初始化延时、TIM3（ARR=99、PSC=71）、TIM1 PWM（ARR=49、PSC=71）、NVIC、OLED、ADC+DMA 和按键 EXTI；永久循环把 TIM1 CH2 比较值维持为 25，并执行应用处理。若系统时钟为常见的 72 MHz，TIM3 更新频率约 10 kHz，TIM1 PWM 约 20 kHz、占空比约 50%。

## 已确认硬件接线

残缺接线图已经与固件交叉验证，确定 OLED 使用 PB6/PB7 软件 I²C（7 位地址 `0x3C`），编码器旋转使用 PA8/PB9、按压使用 PA12，采集输入为 PA0/ADC1_IN0，PA9/TIM1_CH2 输出约 20 kHz 测试方波。完整证据、电气限制及最终接线表见 `HARDWARE_PINOUT.md`。

## 关键 RAM 布局

以下区域由代码中的固定地址和访问步长直接推得，可信度高：

| RAM 地址 | 推定类型 | 用途 |
|---|---|---|
| `0x200019E0` | `uint16_t` | ADC/DMA 当前采样值 |
| `0x200019E8` | `int32_t[256]` | FFT 输入，实部/虚部以两个 Q15 半字打包 |
| `0x20001DE8` | `int32_t[256]` | FFT 复数输出 |
| `0x200021E8` | `uint16_t[256]` | FFT 幅值 |
| `0x200023E8` | `uint16_t[256]` | 原始 ADC 采样序列 |
| `0x200025E8` | `uint8_t[8][128]` | 128×64 OLED 页式显存，共 1024 字节 |
| `0x20001994` | `uint16_t` | 当前采样下标，达到 256 后清零 |
| `0x20001996/98` | `uint16_t` | 当前帧最大值/最小值 |
| `0x20000FFC` | `uint8_t` | 256 点采集完成标志 |

## 产物说明

- `out/minisbq.bin`：从 Intel HEX 无损提取的连续二进制，加载基址为 `0x08000000`。
- `out/minisbq_recursive.asm`：带函数标签、直接调用目标、RAM/外设 literal 注释的 Thumb 反汇编。
- `out/functions.csv`：147 个已发现入口及反汇编指令数。
- `out/calls.csv`：466 条直接调用边，可用于继续重命名函数。
- `out/literal_references.csv`：RAM、Flash、STM32F1 外设引用。
- `out/analysis.json`：HEX 元数据、向量表、字符串扫描和地址段信息。
- `recovered_core.c`：main、TIM3 采样 ISR、OLED 清屏等核心逻辑的人工 C 伪代码。
- `HARDWARE_PINOUT.md`：由接线图与固件共同确认的最终引脚表和电气安全说明。
- `ASSEMBLY_GUIDE.md`：基于 STM32F103C8T6 最小系统板的点对点焊接、SWD 烧录和上电验证步骤。
- `hardware/接线_原图.png`：用户提供的残缺接线图原件归档。
- `hardware/材料_原图.png`：用户提供的最小系统板、OLED、EC11 与 ST-Link 材料图原件归档。

递归分析已覆盖约 69.3% 的镜像字节；其余主要是字体/查找表、literal pool、初始化数据及未从当前调用链抵达的 ARMCC 数学库代码，不应简单按代码线性解释。

## 无法从 release 固件恢复的内容

原始变量名、绝大多数函数名、注释、头文件组织、宏、枚举名称和精确源文件边界已被编译器删除。C 伪代码能继续扩展到接近原逻辑，但不会与丢失源码逐字一致。镜像中也没有可用的调试符号；所谓 ASCII 字符串多数是 Thumb 指令或字体数据误判。

下一轮最有价值的是结合原理图/PCB 引脚表确认 OLED 接线、按键脚和 ADC 通道，然后以 STM32F103C8T6、`STM32F10X_MD` 为目标，按模块把反汇编继续还原为可编译的 STM32F1 SPL/HAL 工程。
