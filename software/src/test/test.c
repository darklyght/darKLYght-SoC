#include <stdarg.h>
#include <stdbool.h>

#define UART_ADDRESS 0x40000000
#define UART_READ_DATA_OFFSET 0x00
#define UART_READ_INFO_OFFSET 0x10
#define UART_WRITE_DATA_OFFSET 0x20
#define UART_WRITE_INFO_OFFSET 0x30

#define CSR_MSTATUS 0x300
#define CSR_MSTATUS_MIE_MASK (1 << 3)
#define CSR_MIE 0x304
#define CSR_MIE_MSIE_MASK (1 << 3)
#define CSR_MIE_MTIE_MASK (1 << 7)
#define CSR_MIE_MEIE_MASK (1 << 11)
#define CSR_MTVEC 0x305
#define CSR_MTVEC_MODE_MASK 0x3
#define CSR_MTVEC_BASE_MASK 0xFFFFFFFC

void __attribute__ ((interrupt("machine"))) riscv_mtvec_exception(void) {
    printf("Here!\n\r");
}

void __attribute__ ((interrupt("machine"))) riscv_mtvec_mti(void) {
    printf("Timer interrupt!\n\r");
    *(unsigned int*)0x60000020 = 0;
}

void __attribute__ ((naked, section(".text.mtvec_table") ,aligned(16))) riscv_mtvec_table(void) {
    asm volatile (
        ".org  riscv_mtvec_table + 0*4;"
        "jal   zero,riscv_mtvec_exception;"
        ".org  riscv_mtvec_table + 3*4;"
        "jal   zero,riscv_mtvec_msi;"
        ".org  riscv_mtvec_table + 7*4;"
        "jal   zero,riscv_mtvec_mti;"
        ".org  riscv_mtvec_table + 11*4;"
        "jal   zero,riscv_mtvec_mei;"
        :
        :
    );
}

inline unsigned int csr_read(unsigned int csr_num) {
    unsigned int result;
    asm volatile("csrrs %[result], %[csr_num], x0"
                 : [result] "=r"(result)
                 : [csr_num] "I"(csr_num));
    return result;
}

inline void csr_write(unsigned int csr_num, unsigned int value) {
    asm volatile ("csrrw x0, %[csr_num], %[value]"
                  : 
                  : [csr_num] "I"(csr_num),
                    [value] "r" (value));
}

void putc(char c) {
    while (!(*(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_INFO_OFFSET) & 0x80000000));
    *(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_DATA_OFFSET) = c;
}

void puts(char* string) {
    char* pointer;

    for (pointer = string; *pointer != '\0'; pointer++) {
        while (!(*(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_INFO_OFFSET) & 0x80000000));
        *(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_DATA_OFFSET) = *pointer;
    }
}


char* num2str(unsigned int num, unsigned int base) {
    static char dict[] = "0123456789ABCDEF";
    static char buffer[20];
    char* pointer;

    pointer = &buffer[19];
    *pointer = '\0';
    do {
        *--pointer = dict[num % base];
        num = num / base;
    } while (num != 0);

    return pointer;
}

void printf(char* format, ...) {
    char* pointer;
    unsigned int arg_value;
    char* arg_string;
    static char buffer[20];

    va_list arg;
    va_start(arg, format);

    for (pointer = format; *pointer != '\0'; pointer++) {
        if (*pointer != '%') {
            putc(*pointer);
        } else {
            pointer++;
            switch (*pointer) {
                case 'c':
                    arg_value = va_arg(arg, unsigned int);
                    putc(arg_value);
                    break;
                case 'd':
                    arg_value = va_arg(arg, int);
                    if (arg_value < 0) {
                        arg_value = -arg_value;
                        putc('-');
                    }
                    puts(num2str(arg_value, 10));
                    break;
                case 's':
                    arg_string = va_arg(arg, char*);
                    puts(arg_string);
                    break;
                case 'x':
                    arg_value = va_arg(arg, unsigned int);
                    puts(num2str(arg_value, 16));
                default:
                    break;
            }
        }
    }

    va_end(arg);
}

void enable_interrupts(void) {
    csr_write(CSR_MTVEC, (0x1 & CSR_MTVEC_MODE_MASK) | ((unsigned int)riscv_mtvec_table & CSR_MTVEC_BASE_MASK));
    csr_write(CSR_MSTATUS, csr_read(CSR_MIE) | CSR_MSTATUS_MIE_MASK);
    csr_write(CSR_MIE, csr_read(CSR_MIE) | CSR_MIE_MSIE_MASK | CSR_MIE_MTIE_MASK | CSR_MIE_MEIE_MASK);
}

int main(void) {
    printf("%s\n", "Start!");
    enable_interrupts();
    asm("wfi"::);
    return 0;
}