#include <pty.h>
#include <printf.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <stdio.h>
#include "svdpi.h"
#include "Vtb__Dpi.h"

typedef struct {
    char name[64];
    int master;
    int slave;
    char data;
} uart_pty_t;

void* uart_create(const char* name) {
    uart_pty_t* port = (uart_pty_t*)malloc(sizeof(*port));
    
    struct termios tty;
    cfmakeraw(&tty);

    openpty(&(port->master), &(port->slave), (char*)name, &tty, NULL);
    int val = ttyname_r(port->slave, port->name, 64);
    (void) val;
    
    printf("UART at Device: %s is ready.\n", port->name);
    
    fcntl(port->master, F_SETFL, fcntl(port->master, F_GETFL, 0) | O_NONBLOCK);

    return (void*) port;
}

int uart_tx_valid(void* port) {
    return read(((uart_pty_t*)port)->master, &(((uart_pty_t*)port)->data), 1) == 1;
}

char uart_tx_data(void* port) {
    return ((uart_pty_t*)port)->data;
}

void printchar(char c) {
    switch (c) {
        case '\n':
            printf("\\n");
            break;
        case '\r':
            printf("\\r");
            break;
        case '\t':
            printf("\\t");
            break;
        default:
            if ((c < 0x20) || (c > 0x7f)) {
                printf("\\%03o", c);
            } else {
                printf("%c", c);
            }
            break;
    }
}

void uart_rx(void* port, char data) {
    printf("UART received: %02X (", data);
    printchar(data);
    printf(")\n");
    int val = write(((uart_pty_t*)port)->master, &data, 1);
    (void) val;
    return;
}
