import socket
import select
import binascii
import random
import time

TARGET_IP = "192.168.1.128"
TARGET_PORT = 1234
# TARGET_IP = "127.0.0.128"
# TARGET_PORT = 8000
ERROR_COUNT = 0

def raw_read(udp_sock, packet, length):
    # global ERROR_COUNT
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    # select.select([udp_sock], [], [], 0)
    data, addr = udp_sock.recvfrom(length)
    if len(data) != length:
        # print("Incorrect length received. Retrying")
        # ERROR_COUNT += 1
        return False
    return data

def raw_write(udp_sock, packet):
    udp_sock.sendto(packet, (TARGET_IP, TARGET_PORT))
    # select.select([udp_sock], [], [], 0)
    data, addr = udp_sock.recvfrom(1)
    return binascii.hexlify(data) == b"00"

def read(udp_sock, address, length):
    assert(length <= 256), "Trying to read more than 256 words."
    # global ERROR_COUNT
    packet = ''
    packet = packet + "0".zfill(2) # R/W bit - 0 for read
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(length - 1) # length - 1 because 0 represents 1 word read
    packet = binascii.unhexlify(packet)
    rv = False
    try:
        rv = raw_read(udp_sock, packet, length * 4)
    except (socket.timeout, BlockingIOError):
        pass
        # print("Did not receive response on read. Trying again.")
        # ERROR_COUNT += 1
    while not rv:
        try:
            rv = raw_read(udp_sock, packet, length * 4)
        except (socket.timeout, BlockingIOError):
            pass
            # print("Did not receive response on read. Trying again.")
            # ERROR_COUNT += 1
    data = [ binascii.hexlify(rv[i:i+4]) for i in range(0, length * 4, 4) ]
    return data

def write(udp_sock, address, data):
    assert(len(data) <= 256), "Data longer than 256 words."
    # global ERROR_COUNT
    packet = ''
    packet = packet + "1".zfill(2) # R/W bit - 1 for write
    packet = packet + address.zfill(8).upper() # 32-bit address
    packet = packet + '{:02x}'.format(len(data) - 1) # length - 1 because 0 represents 1 word write
    packet = packet + ''.join([ word.zfill(8).upper() for word in data ])
    packet = binascii.unhexlify(packet)
    rv = False
    try:
        rv = raw_write(udp_sock, packet)
    except (socket.timeout, BlockingIOError):
        pass
        # print("Did not receive response on write. Trying again.")
        # ERROR_COUNT += 1
    while not rv:
        try:
            rv = raw_write(udp_sock, packet)
        except (socket.timeout, BlockingIOError):
            pass
            # print("Did not receive response on write. Trying again.")
            # ERROR_COUNT += 1
    return len(data)


udp_sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
udp_sock.bind(("192.168.1.8", 40000))
udp_sock.settimeout(0.000001)

random_data = ['%08x' % random.randrange(16**8) for n in range(256)]

start = time.time()
for i in range(0, 10000):
    # print(i)
    # random_data = ['%08x' % random.randrange(16**8) for n in range(256)]
    write(udp_sock, "00000000", random_data)
    data = read(udp_sock, "00000000", 256)
    error = False

    for j in range(0, 256):
        if binascii.hexlify(binascii.unhexlify(random_data[j])) != data[j]:
            error = True
            break
    
    if error:
        print(random_data, data)
        print(i, "Error")
        exit(1)

end = time.time()
print(end - start)

# print(str(ERROR_COUNT) + " errors")