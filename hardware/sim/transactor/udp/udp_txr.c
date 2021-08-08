#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <netinet/in.h>
#include "svdpi.h"
#include "Vtb__Dpi.h"

#define BUFFER_SIZE 4096

typedef struct {
    int fd;
    struct sockaddr_in server_address;
    struct sockaddr_in client_address;
    unsigned int client_address_length;
    char rx_buffer[BUFFER_SIZE];
    char tx_buffer[BUFFER_SIZE];
    int data_length;
    int rx_pointer;
    int tx_pointer;
} udp_master_t;

void* udp_create(int port_number) {
    udp_master_t* port = (udp_master_t*)malloc(sizeof(*port));
    port->rx_pointer = 0;
    port->tx_pointer = 0;
    
    bzero(&(port->server_address), sizeof(port->server_address));
    
    port->fd = socket(AF_INET, SOCK_DGRAM, 0);
    (port->server_address).sin_family = AF_INET;
    (port->server_address).sin_addr.s_addr = inet_addr("127.0.0.1");
    (port->server_address).sin_port = htons((in_port_t)port_number);
    
    fcntl(port->fd, F_SETFL, fcntl(port->fd, F_GETFL, 0) | O_NONBLOCK);
    
    bind(port->fd, (struct sockaddr*)&(port->server_address), sizeof(port->server_address));
    
    port->client_address_length = sizeof(((udp_master_t*)port)->client_address);
    
    return (void*) port;
}

int udp_tx_valid(void* port) {
    if (((udp_master_t*)port)->tx_pointer == 0) {
        int size = recvfrom(((udp_master_t*)port)->fd, ((udp_master_t*)port)->tx_buffer, sizeof(((udp_master_t*)port)->tx_buffer), 0, (struct sockaddr*)&(((udp_master_t*)port)->client_address), &(((udp_master_t*)port)->client_address_length));
        if (size > 0) {
            int port_number = (int)ntohs((((udp_master_t*)port)->client_address).sin_port);
            printf("%d\n", port_number);
            ((udp_master_t*)port)->data_length = size;
            ((udp_master_t*)port)->tx_pointer = size;
        }
    }
    return ((udp_master_t*)port)->tx_pointer;
}

char udp_tx_data(void* port) {
    char data = ((udp_master_t*)port)->tx_buffer[((udp_master_t*)port)->data_length - ((udp_master_t*)port)->tx_pointer];
    if (((udp_master_t*)port)->tx_pointer > 0) {
        printf("Sent: %c\n", data);
        ((udp_master_t*)port)->tx_pointer -= 1;
    }
    return data;
}

void udp_rx(void* port, char data, int last) {
    ((udp_master_t*)port)->rx_buffer[((udp_master_t*)port)->rx_pointer] = data;
    ((udp_master_t*)port)->rx_pointer += 1;
    if (last) {
        sendto(((udp_master_t*)port)->fd, ((udp_master_t*)port)->rx_buffer, ((udp_master_t*)port)->rx_pointer, 0, (struct sockaddr*)&(((udp_master_t*)port)->client_address), ((udp_master_t*)port)->client_address_length);
        ((udp_master_t*)port)->rx_pointer = 0;
    }
}