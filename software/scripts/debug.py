import socket
import select
import binascii
import random
import time
import argparse

def raw_read(udp_sock, packet, length, simulation=False):
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    if simulation:
        time.sleep(1)
    data, addr = udp_sock.recvfrom(length + 1)
    if simulation:
        time.sleep(1)
    if data[-1] >> 6 == 3:
        print("Read decode error")
    if len(data) != length + 1:
        return False
    return data

def raw_write(udp_sock, packet, simulation=False):
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    if simulation:
        time.sleep(1)
    data, addr = udp_sock.recvfrom(1)
    if simulation:
        time.sleep(1)
    if binascii.hexlify(data) == b"03":
        print("Write decode error")
    return binascii.hexlify(data) == b"00"

def read(udp_sock, address, length, simulation=False):
    assert(length <= 256), "Trying to read more than 256 words."
    packet = ''
    packet = packet + "0".zfill(2) # R/W bit - 0 for read
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(length - 1) # length - 1 because 0 represents 1 word read
    packet = binascii.unhexlify(packet)
    rv = raw_read(udp_sock, packet, length * 4, simulation)
    data = [ binascii.hexlify(rv[i:i+4]) for i in range(0, length * 4, 4) ]
    return data

def write(udp_sock, address, data, simulation=False):
    assert(len(data) <= 256), "Data longer than 256 words."
    packet = ''
    packet = packet + "1".zfill(2) # R/W bit - 1 for write
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(len(data) - 1) # length - 1 because 0 represents 1 word write
    packet = packet + ''.join([ word.zfill(8).upper() for word in data ])
    packet = binascii.unhexlify(packet)
    rv = raw_write(udp_sock, packet, simulation)
    return len(data)

parser = argparse.ArgumentParser(description='Settings for sending and receiving packets.')
parser.add_argument('src_ip', help='Source IP (IP to send packets from)')
parser.add_argument('src_port', help='Source port (Port to send packets from)')
parser.add_argument('dst_ip', help='Destination IP (IP to send packets to)')
parser.add_argument('dst_port', help='Destination port (Port to send packets to)')
parser.add_argument('--test_addr', help='Starting address for test')
parser.add_argument('--simulation', help='Simulator-friendly packet sending', action='store_true')
args = parser.parse_args()

SOURCE_IP = args.src_ip
SOURCE_PORT = int(args.src_port)
TARGET_IP = args.dst_ip
TARGET_PORT = int(args.dst_port)

udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
udp_sock.bind((SOURCE_IP, SOURCE_PORT))
udp_sock.settimeout(5)

start = time.time()
for i in range(0, 10):
    random_data = ['%08x' % random.randrange(16**8) for n in range(256)]
    write(udp_sock, args.test_addr if args.test_addr else "20000000", random_data, args.simulation)
    data = read(udp_sock, args.test_addr if args.test_addr else "20000000", 256, args.simulation)
    error = False

    for j in range(0, 256):
        if binascii.hexlify(binascii.unhexlify(random_data[j])) != data[j]:
            error = True
            break
    
    if error:
        print(i, "Error")
        exit(1)

end = time.time()
print(end - start)