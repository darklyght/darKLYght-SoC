#define UART_ADDRESS 0x40000000
#define UART_READ_DATA_OFFSET 0x00
#define UART_READ_INFO_OFFSET 0x10
#define UART_WRITE_DATA_OFFSET 0x20
#define UART_WRITE_INFO_OFFSET 0x30

void puts(char* string) {
    char* pointer;

    for (pointer = string; *pointer != '\0'; pointer++) {
        while (!(*(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_INFO_OFFSET) & 0x80000000));
        *(volatile unsigned int *)(UART_ADDRESS + UART_WRITE_DATA_OFFSET) = *pointer;
    }
}

int main(void) {
    puts("Testing!");
    while (1);
    return 0;
}