import socket
import select
import binascii
import random
import time
import argparse

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description='Settings for sending and receiving packets.')
    parser.add_argument('src_ip', help='Source IP (IP to send packets from)')
    parser.add_argument('src_port', help='Source port (Port to send packets from)')
    parser.add_argument('dst_ip', help='Destination IP (IP to send packets to)')
    parser.add_argument('dst_port', help='Destination port (Port to send packets to)')
    parser.add_argument('--simulation', help='Simulator-friendly packet sending', action='store_true')
    args = parser.parse_args()

    SOURCE_IP = args.src_ip
    SOURCE_PORT = int(args.src_port)
    TARGET_IP = args.dst_ip
    TARGET_PORT = int(args.dst_port)

    udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    udp_sock.bind((SOURCE_IP, SOURCE_PORT))
    udp_sock.settimeout(5)

def raw_read(packet, length, simulation=False):
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    if simulation:
        time.sleep(1)
    data, addr = udp_sock.recvfrom(length + 1)
    if simulation:
        time.sleep(1)
    if data[-1] << 2 >> 6 == 2:
        print("Read slave error: Crossing 4K boundary on read burst or reading invalid register")
    elif data[-1] >> 6 == 3:
        print("Read decode error")
    if len(data) != length + 1:
        return False
    return data

def raw_write(packet, simulation=False):
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    if simulation:
        time.sleep(1)
    data, addr = udp_sock.recvfrom(1)
    if simulation:
        time.sleep(1)
    if binascii.hexlify(data) == b"02":
        print("Write slave error: Crossing 4K boundary on write burst or writing invalid register")
    elif binascii.hexlify(data) == b"03":
        print("Write decode error")
    return binascii.hexlify(data) == b"00"

def read(address, length, simulation=False):
    assert(length <= 256), "Trying to read more than 256 words."
    assert(int(address, 16) // 4096 == (int(address, 16) + length - 1) // 4096), "Read burst crosses 4K boundary"
    packet = ''
    packet = packet + "0".zfill(2) # R/W bit - 0 for read
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(length - 1) # length - 1 because 0 represents 1 word read
    packet = binascii.unhexlify(packet)
    rv = raw_read(packet, length * 16, simulation)
    data = [ binascii.hexlify(rv[i:i+16]) for i in range(0, length * 16, 16) ]
    return data

def read_32bit(address, simulation=False):
    if address[-1].upper() == "0":
        return read(address, 1, simulation)[0][24:32]
    elif address[-1].upper() == "4":
        return read(address, 1, simulation)[0][16:24]
    elif address[-1].upper() == "8":
        return read(address, 1, simulation)[0][8:16]
    elif address[-1].upper() == "C":
        return read(address, 1, simulation)[0][0:8]

def write(address, data, simulation=False):
    return write_strobe(address, data, "F", simulation)

def write_strobe(address, data, strb, simulation=False):
    assert(len(data) <= 256), "Data longer than 256 words."
    assert(int(address, 16) // 4096 == (int(address, 16) + len(data) - 1) // 4096), "Write burst crosses 4K boundary"
    assert(len(strb) == 1), "Strobe length should be 1"
    packet = ''
    packet = packet + strb
    packet = packet + "1" # R/W bit - 1 for write
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(len(data) - 1) # length - 1 because 0 represents 1 word write
    packet = packet + ''.join([ word.zfill(32).upper() for word in data ])
    packet = binascii.unhexlify(packet)
    rv = raw_write(packet, simulation)
    return rv

def write_32bit(address, data, simulation=False):
    if address[-1].upper() == "0":
        return write_strobe(address, [data + "0" * 0], "1", simulation)
    elif address[-1].upper() == "4":
        return write_strobe(address, [data + "0" * 8], "2", simulation)
    elif address[-1].upper() == "8":
        return write_strobe(address, [data + "0" * 16], "4", simulation)
    elif address[-1].upper() == "C":
        return write_strobe(address, [data + "0" * 24], "8", simulation)

def write_music(filename, address):
    with open(filename, 'r') as f:
        contents = f.read().split('\n')
        for i in range(0, len(contents), 64):
            print('{:.2f}%'.format(float(i) / float(len(contents)) * 100), end='\r')
            data = contents[i:i+64]
            addr = '{:X}'.format(int(address, 16) + (i * 16))
            write(addr, data)
        print('\n')
        return len(contents)

def play_music(filename, address):
    write_32bit("60000018", address)
    write_32bit("60000014", "00000000")
    length = write_music(filename, address)
    write_32bit("60000014", '{:08x}'.format(int("80000000", 16) + length))

def stop_music():
    write_32bit("60000014", "00000000")
