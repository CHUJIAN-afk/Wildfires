/*
 * Human-reconstructed pseudocode from minisbq.hex.
 *
 * This is an analysis aid, not buildable source. Names and C types are inferred;
 * instruction addresses and RAM addresses in the comments come from the image.
 */

#include <stdint.h>

typedef struct TIM_TypeDef TIM_TypeDef;
extern TIM_TypeDef *const TIM1;
extern TIM_TypeDef *const TIM3;

/* Strongly supported RAM layout recovered from literal references. */
static volatile uint16_t *const adc_latest       = (uint16_t *)0x200019E0;
static volatile uint16_t *const sample_index     = (uint16_t *)0x20001994;
static volatile uint16_t *const sample_max       = (uint16_t *)0x20001996;
static volatile uint16_t *const sample_min       = (uint16_t *)0x20001998;
static volatile uint8_t  *const capture_ready    = (uint8_t  *)0x20000FFC;
static volatile int32_t  *const fft_input_q15x2  = (int32_t  *)0x200019E8;
static volatile int32_t  *const fft_output_q15x2 = (int32_t  *)0x20001DE8;
static volatile uint16_t *const fft_magnitude    = (uint16_t *)0x200021E8;
static volatile uint16_t *const adc_samples      = (uint16_t *)0x200023E8;
static volatile uint8_t  *const oled_gram        = (uint8_t  *)0x200025E8;

/* Inferred names. The address comments are the authoritative identifiers. */
extern void delay_init(void);                         /* 0x08003234 */
extern void TIM3_Int_Init(uint16_t arr, uint16_t psc);/* 0x08002DE8 */
extern void TIM1_PWM_Init(uint16_t arr, uint16_t psc);/* 0x08002C80 */
extern void NVIC_PriorityGroupConfig(uint32_t group); /* 0x0800262C */
extern void OLED_Init(void);                          /* 0x08002870 */
extern void ADC_DMA_Init(void);                       /* 0x08001D14 */
extern void Key_EXTI_Init(void);                      /* 0x08001F3A */
extern void OLED_Clear(void);                         /* 0x08002640 */
extern void TIM_SetCompare2(TIM_TypeDef *, uint16_t); /* 0x08002F82 */
extern void Application_Process(void);                /* 0x0800302C */

/* Recovered from 0x08003464. */
int recovered_main(void)
{
    delay_init();
    TIM3_Int_Init(99, 71);       /* 72 MHz assumption -> 10 kHz update rate. */
    TIM1_PWM_Init(49, 71);       /* 72 MHz assumption -> 20 kHz PWM. */
    NVIC_PriorityGroupConfig(0x500); /* NVIC_PriorityGroup_2 in STM32 SPL. */
    OLED_Init();
    Application_Process();       /* One initial state/display pass. */
    ADC_DMA_Init();
    Key_EXTI_Init();
    OLED_Clear();

    for (;;) {
        TIM_SetCompare2(TIM1, 25); /* 50% duty for ARR=49. */
        Application_Process();
    }
}

/*
 * Recovered semantically from 0x08002D18. SPL helper calls are compressed here;
 * the assembly output preserves the exact calls and ordering.
 */
void recovered_TIM3_IRQHandler(void)
{
    if (TIM_GetITStatus(TIM3, 1 /* TIM_IT_Update */)) {
        uint16_t i = *sample_index;
        uint16_t sample = *adc_latest;

        adc_samples[i] = sample;
        if (sample > *sample_max) *sample_max = sample;
        if (sample < *sample_min) *sample_min = sample;

        /* Low halfword is zero, high halfword is the ADC sample: complex Q15 input. */
        fft_input_q15x2[i] = (int32_t)((uint32_t)sample << 16);

        i++;
        *sample_index = i;
        if (i == 256) {
            *sample_index = 0;
            *capture_ready = 1;
            TIM_ITConfig(TIM3, 1 /* TIM_IT_Update */, 0 /* DISABLE */);
        }
        TIM_ClearITPendingBit(TIM3, 1 /* TIM_IT_Update */);
    }
}

/* Recovered from 0x08002640. */
void recovered_OLED_Clear(void)
{
    for (uint8_t page = 0; page < 8; ++page)
        for (uint8_t column = 0; column < 128; ++column)
            oled_gram[page * 128u + column] = 0;
    OLED_Refresh_Gram();
}

/*
 * FFT_Process_256 at 0x080022E4 calls the fixed-point kernel at 0x08000274 with
 * fft_output_q15x2, fft_input_q15x2, and length 256. It then calculates complex
 * magnitudes into fft_magnitude[0..255] using ARMCC soft-float math helpers.
 */
