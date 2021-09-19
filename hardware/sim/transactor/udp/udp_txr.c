#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <string.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <net/if.h>
#include <net/ethernet.h>
#include <netinet/ip_icmp.h>
#include <netinet/tcp.h>
#include <netinet/udp.h>
#include <netinet/ip.h>
#include <netinet/in.h>
#include <netinet/if_ether.h>
#include <linux/if_packet.h>
#include <arpa/inet.h>
#include <errno.h>
#include "svdpi.h"
#include "Vtb__Dpi.h"

#define BUFFER_SIZE 4096
#define IP_ADDRESS "192.168.0.128"
#define INTERFACE "enp3s0"
#define MAX_PORTS 100

typedef struct {
    int fd;
    struct sockaddr_in server_address;
} udp_master_t;

typedef struct {
    int fd;
    struct sockaddr_ll server_address;
    char rx_buffer[BUFFER_SIZE];
    char tx_buffer[BUFFER_SIZE];
    int data_length;
    int rx_pointer;
    int tx_pointer;
    int port_numbers[MAX_PORTS];
    udp_master_t* ports[MAX_PORTS];
    int n_ports;
} eth_master_t;

char* get_destination_ip(char* buffer);
char* get_data(char* buffer);
void process_packet(char* buffer, int size);
void print_ethernet_header(char* buffer, int size);
void print_ip_header(char* buffer, int size);
void print_tcp_packet(char* buffer, int size);
void print_udp_packet(char* buffer, int size);
void print_icmp_packet(char* buffer, int size);
void print_data(char* data , int size);

void* eth_create() {
    eth_master_t* eth = (eth_master_t*)malloc(sizeof(*eth));
    eth->data_length = 0;
    eth->tx_pointer = 0;
    eth->rx_pointer = 0;
    eth->n_ports = 0;

    bzero(&(eth->server_address), sizeof(eth->server_address));

    eth->fd = socket(AF_PACKET, SOCK_RAW, htons(ETH_P_ALL));
    (eth->server_address).sll_family = AF_PACKET;
    (eth->server_address).sll_ifindex = if_nametoindex(INTERFACE);
    (eth->server_address).sll_protocol = htons(ETH_P_ALL);

    fcntl(eth->fd, F_SETFL, fcntl(eth->fd, F_GETFL, 0) | O_NONBLOCK);

    bind(eth->fd, (struct sockaddr*)&(eth->server_address), sizeof(eth->server_address));

    return (void*) eth;
}

void udp_create(void* eth, int port_number) {
    udp_master_t* port = (udp_master_t*)malloc(sizeof(*port));
    
    bzero(&(port->server_address), sizeof(port->server_address));
    
    port->fd = socket(AF_INET, SOCK_DGRAM, 0);
    (port->server_address).sin_family = AF_INET;
    (port->server_address).sin_addr.s_addr = inet_addr(IP_ADDRESS);
    (port->server_address).sin_port = htons((in_port_t)port_number);
    
    fcntl(port->fd, F_SETFL, fcntl(port->fd, F_GETFL, 0) | O_NONBLOCK);
    
    bind(port->fd, (struct sockaddr*)&(port->server_address), sizeof(port->server_address));

    ((eth_master_t*)eth)->port_numbers[((eth_master_t*)eth)->n_ports] = port_number;
    ((eth_master_t*)eth)->ports[((eth_master_t*)eth)->n_ports] = port;
    ((eth_master_t*)eth)->n_ports++;

    printf("UDP on IP Address: %s Port: %d is ready.\n", IP_ADDRESS, port_number);

    return;
}

int eth_tx_valid(void* eth) {
    if (((eth_master_t*)eth)->tx_pointer == 0) {
        int size = recvfrom(((eth_master_t*)eth)->fd, ((eth_master_t*)eth)->tx_buffer, sizeof(((eth_master_t*)eth)->tx_buffer), 0, NULL, NULL);
        if (size > 0 && strcmp(get_destination_ip(((eth_master_t*)eth)->tx_buffer), IP_ADDRESS) == 0) {
            ((eth_master_t*)eth)->tx_buffer[size] = '\0';
            process_packet(((eth_master_t*)eth)->tx_buffer, size);
            ((eth_master_t*)eth)->data_length = size;
            ((eth_master_t*)eth)->tx_pointer = size;
        }
    }
    return ((eth_master_t*)eth)->tx_pointer;
}

char eth_tx_data(void* eth) {
    char data = ((eth_master_t*)eth)->tx_buffer[((eth_master_t*)eth)->data_length - ((eth_master_t*)eth)->tx_pointer];
    if (((eth_master_t*)eth)->tx_pointer > 0) {
        ((eth_master_t*)eth)->tx_pointer -= 1;
    }
    return data;
}

void eth_rx(void* eth, char data, int last) {
    ((eth_master_t*)eth)->rx_buffer[((eth_master_t*)eth)->rx_pointer] = data;
    ((eth_master_t*)eth)->rx_pointer += 1;
    if (last) {
        ((eth_master_t*)eth)->rx_buffer[((eth_master_t*)eth)->rx_pointer] = '\0';
        process_packet(((eth_master_t*)eth)->rx_buffer, ((eth_master_t*)eth)->rx_pointer);
        //sendto(((udp_master_t*)port)->fd, ((udp_master_t*)port)->rx_buffer, ((udp_master_t*)port)->rx_pointer, 0, (struct sockaddr*)&(((udp_master_t*)port)->client_address), ((udp_master_t*)port)->client_address_length);
        ((eth_master_t*)eth)->rx_pointer = 0;
    }
}

char* get_destination_ip(char* buffer) {
    struct iphdr *iph = (struct iphdr*)(buffer + sizeof(struct ethhdr));
    sockaddr_in dest;

    memset(&dest, 0, sizeof(dest));
    dest.sin_addr.s_addr = iph->daddr;
    
    return inet_ntoa(dest.sin_addr);
}

char* get_source_i

char* get_data(char* buffer) {
    struct iphdr *iph = (struct iphdr *)(buffer + sizeof(struct ethhdr));
	int iphdrlen = iph->ihl*4;
	
	struct udphdr *udph = (struct udphdr*)(buffer + iphdrlen  + sizeof(struct ethhdr));

    int header_size =  sizeof(struct ethhdr) + iphdrlen + sizeof(udph);

    return buffer + header_size;
}

void process_packet(char* buffer, int size) {
	struct iphdr *iph = (struct iphdr*)(buffer + sizeof(struct ethhdr));
	switch (iph->protocol) {
		case 1:  //ICMP Protocol
			print_icmp_packet(buffer, size);
			break;
		
		case 2:  //IGMP Protocol
			break;
		
		case 6:  //TCP Protocol
			print_tcp_packet(buffer, size);
			break;
		
		case 17: //UDP Protocol
			print_udp_packet(buffer, size);
			break;
		
		default: //Some Other Protocol like ARP etc.
			break;
	}
}

void print_ethernet_header(char* buffer, int size) {
	struct ethhdr *eth = (struct ethhdr *) buffer;
	
	printf("\n");
	printf("Ethernet Header\n");
	printf("   |-Destination Address : %.2X-%.2X-%.2X-%.2X-%.2X-%.2X\n", eth->h_dest[0], eth->h_dest[1], eth->h_dest[2], eth->h_dest[3], eth->h_dest[4], eth->h_dest[5]);
	printf("   |-Source Address      : %.2X-%.2X-%.2X-%.2X-%.2X-%.2X\n", eth->h_source[0], eth->h_source[1], eth->h_source[2], eth->h_source[3], eth->h_source[4], eth->h_source[5]);
	printf("   |-Protocol            : %u\n", (unsigned short)eth->h_proto);
}

void print_ip_header(char* buffer, int size) {
	print_ethernet_header(buffer, size);

	unsigned short iphdrlen;		
	struct iphdr *iph = (struct iphdr *)(buffer  + sizeof(struct ethhdr) );
	iphdrlen =iph->ihl*4;
	
    struct sockaddr_in source, dest;
	memset(&source, 0, sizeof(source));
	source.sin_addr.s_addr = iph->saddr;
	memset(&dest, 0, sizeof(dest));
	dest.sin_addr.s_addr = iph->daddr;
	
	printf("\n");
	printf("IP Header\n");
	printf("   |-IP Version          : %d\n", (unsigned int)iph->version);
	printf("   |-IP Header Length    : %d bytes\n", ((unsigned int)(iph->ihl))*4);
	printf("   |-Type Of Service     : %d\n", (unsigned int)iph->tos);
	printf("   |-IP Total Length     : %d bytes\n", ntohs(iph->tot_len));
	printf("   |-Identification      : %d\n", ntohs(iph->id));
	printf("   |-TTL                 : %d\n", (unsigned int)iph->ttl);
	printf("   |-Protocol            : %d\n", (unsigned int)iph->protocol);
	printf("   |-Checksum            : %d\n", ntohs(iph->check));
	printf("   |-Source IP           : %s\n", inet_ntoa(source.sin_addr));
	printf("   |-Destination IP      : %s\n", inet_ntoa(dest.sin_addr));
}

void print_tcp_packet(char* buffer, int size) {
	unsigned short iphdrlen;
	
	struct iphdr *iph = (struct iphdr *)(buffer  + sizeof(struct ethhdr) );
	iphdrlen = iph->ihl*4;
	
	struct tcphdr *tcph=(struct tcphdr*)(buffer + iphdrlen + sizeof(struct ethhdr));
			
	int header_size =  sizeof(struct ethhdr) + iphdrlen + tcph->doff * 4;
	
	printf("\n\n***********************TCP Packet*************************\n");	
		
	print_ip_header(buffer, size);
		
	printf("\n");
	printf("TCP Header\n");
	printf("   |-Source Port          : %u\n", ntohs(tcph->source));
	printf("   |-Destination Port     : %u\n", ntohs(tcph->dest));
	printf("   |-Sequence Number      : %u\n", ntohl(tcph->seq));
	printf("   |-Acknowledge Number   : %u\n", ntohl(tcph->ack_seq));
	printf("   |-Header Length        : %d bytes\n", (unsigned int)tcph->doff*4);
	printf("   |-Urgent Flag          : %d\n", (unsigned int)tcph->urg);
	printf("   |-Acknowledgement Flag : %d\n", (unsigned int)tcph->ack);
	printf("   |-Push Flag            : %d\n", (unsigned int)tcph->psh);
	printf("   |-Reset Flag           : %d\n", (unsigned int)tcph->rst);
	printf("   |-Synchronise Flag     : %d\n", (unsigned int)tcph->syn);
	printf("   |-Finish Flag          : %d\n", (unsigned int)tcph->fin);
	printf("   |-Window               : %d\n", ntohs(tcph->window));
	printf("   |-Checksum             : %d\n", ntohs(tcph->check));
	printf("   |-Urgent Pointer       : %d\n", tcph->urg_ptr);

	printf("Data Payload\n");	
	print_data(buffer + header_size , size - header_size);
						
	printf("\n###########################################################");
}

void print_udp_packet(char* buffer, int size) {
	unsigned short iphdrlen;
	
	struct iphdr *iph = (struct iphdr *)(buffer +  sizeof(struct ethhdr));
	iphdrlen = iph->ihl*4;
	
	struct udphdr *udph = (struct udphdr*)(buffer + iphdrlen  + sizeof(struct ethhdr));
	
	int header_size =  sizeof(struct ethhdr) + iphdrlen + sizeof(udph);
	
	printf("\n\n***********************UDP Packet*************************\n");
	
	print_ip_header(buffer, size);			
	
	printf("\nUDP Header\n");
	printf("   |-Source Port        : %d\n", ntohs(udph->source));
	printf("   |-Destination Port   : %d\n", ntohs(udph->dest));
	printf("   |-UDP Length         : %d\n", ntohs(udph->len));
	printf("   |-UDP Checksum       : %d\n", ntohs(udph->check));

	printf("Data Payload\n");	
	print_data(buffer + header_size, size - header_size);
	
	printf("\n###########################################################");
}

void print_icmp_packet(char* buffer, int size) {
	unsigned short iphdrlen;
	
	struct iphdr *iph = (struct iphdr *)(buffer  + sizeof(struct ethhdr));
	iphdrlen = iph->ihl * 4;
	
	struct icmphdr *icmph = (struct icmphdr *)(buffer + iphdrlen  + sizeof(struct ethhdr));
	
	int header_size =  sizeof(struct ethhdr) + iphdrlen + sizeof(icmph);
	
	printf("\n\n***********************ICMP Packet*************************\n");	
	
	print_ip_header(buffer, size);		
	printf("\n");
		
	printf("ICMP Header\n");
	printf("   |-Type                : %d\n", (unsigned int)(icmph->type));
			
	if ((unsigned int)(icmph->type) == 11) {
		printf("  (TTL Expired)\n");
	} else if ((unsigned int)(icmph->type) == ICMP_ECHOREPLY) {
		printf("  (ICMP Echo Reply)\n");
	}
	
	printf("   |-Code                : %d\n", (unsigned int)(icmph->code));
	printf("   |-Checksum            : %d\n", ntohs(icmph->checksum));
	printf("\n");

	printf("Data Payload\n");	
	print_data(buffer + header_size, (size - header_size));
	
    printf("\n###########################################################");
}

void print_data(char* data , int size) {
	int i, j;
	for (i = 0 ; i < size ; i++) {
		if (i != 0 && i % 16 == 0) {
			printf("         ");
			for(j = i - 16; j < i; j++) {
				if (data[j] >= 32 && data[j] <= 128) {
					printf("%c", (char)data[j]);
                } else {
                    printf(".");
                }
			}
			printf("\n");
		} 
		
		if (i % 16 == 0) {
            printf("   ");
        }

        printf(" %02X", data[i] & 0xFF);

		if (i == size - 1) {
			for (j = 0; j < 15 - i % 16; j++) {
			  printf("   ");
			}
			
			printf("         ");
			
			for(j = i - i % 16; j <= i; j++) {
				if (data[j] >= 32 && data[j] <= 128) {
				    printf("%c", (char)data[j]);
				} else {
				  printf(".");
				}
			}
			printf("\n" );
		}
	}
}