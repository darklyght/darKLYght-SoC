import socket
import select
import binascii
import random
import time
import argparse

parser = argparse.ArgumentParser(description='Settings for sending and receiving packets.')
parser.add_argument('src_ip', help='Source IP (IP to send packets from)')
parser.add_argument('src_port', help='Source port (Port to send packets from)')
parser.add_argument('dst_ip', help='Destination IP (IP to send packets to)')
parser.add_argument('dst_port', help='Destination port (Port to send packets to)')

args = parser.parse_args()

SOURCE_IP = args.src_ip
SOURCE_PORT = int(args.src_port)
TARGET_IP = args.dst_ip
TARGET_PORT = int(args.dst_port)

udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
udp_sock.bind((SOURCE_IP, SOURCE_PORT))
# udp_sock.settimeout(5)

memory = dict()

while True:
    readable, writable, exceptional = select.select([udp_sock], [], [])
    if readable:
        data, addr = udp_sock.recvfrom(1037)
        if data[0] == 0:
            # Read packet
            length = data[1] + 1
            address = data[2:6].hex()
            print('Reading {} words starting from address {}'.format(length, address))
            packet = ''
            packet = packet + "0".zfill(2)
            for i in range(0, length * 4, 4):
                offset_address = '{:08x}'.format(int(address, 16) + i)
                if '{:08x}'.format(int(address, 16) + i) not in memory:
                    packet = packet + "0".zfill(8)
                else:
                    packet = packet + memory[offset_address]
                packet = packet + "0".zfill(2) # Read response
            packet = packet + "0".zfill(2)
            packet = binascii.unhexlify(packet)
            udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
        else:
            # Write packet
            length = data[1] + 1
            address = data[2:6].hex()
            print('Writing {} words starting from address {}'.format(length, address))
            packet = ''
            for i in range(0, length * 4, 4):
                offset_address = '{:08x}'.format(int(address, 16) + i)
                memory[offset_address] = data[i+6:i+10].hex()
            packet = packet + "1".zfill(2)
            packet = packet + "0".zfill(2)
            packet = binascii.unhexlify(packet)
            udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
            
