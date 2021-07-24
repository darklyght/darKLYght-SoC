import serial
import binascii
import time

PORT = '/dev/pts/1'
BAUD = 115200

read_bit = {'0': '0',
            '1': '1',
            '2': '2',
            '3': '3',
            '4': '4',
            '5': '5',
            '6': '6',
            '7': '7',
            '8': '0',
            '9': '1',
            'A': '2',
            'B': '3',
            'C': '4',
            'D': '5',
            'E': '6',
            'F': '7'}
write_bit = {'0': '8',
             '1': '9',
             '2': 'A',
             '3': 'B',
             '4': 'C',
             '5': 'D',
             '6': 'E',
             '7': 'F',
             '8': '8',
             '9': '9',
             'A': 'A',
             'B': 'B',
             'C': 'C',
             'D': 'D',
             'E': 'E',
             'F': 'F'}

def read(address):
    address = address.zfill(8)
    address = address.upper()
    address = read_bit[address[0]] + address[1:8]
    ser = serial.Serial(PORT, BAUD)
    time.sleep(0.1)
    ser.write(binascii.unhexlify(address))
    print(binascii.hexlify(ser.read(4)))

def write(address, data):
    address = address.zfill(8)
    data = data.zfill(8)
    address = address.upper()
    address =  write_bit[address[0]] + address[1:8]
    ser = serial.Serial(PORT, BAUD)
    #time.sleep(0.1)
    ser.write(binascii.unhexlify(address + data))
    #address = read_bit[address[0]] + address[1:8]
    #read(address)

if __name__ == '__main__':
#    write("20000004", "1000000")
#    write("20008000", "1aa")
#    print("DRAM Check\n")
#    tic = time.perf_counter()
#    for i in range(0, 1000, 4):
#        write(hex(i)[2:], hex(i)[2:])
#    toc = time.perf_counter()
#    print(f"Wrote took {toc - tic:0.4f} seconds")
#    for i in range(0, 100, 4):
#        read(hex(i)[2:])
#    
#    print("\n\nTimer Check\n")
#    write("20000004", "186a0")
#    for i in range(0, 100):
#        read("20000000")
#
#    print("\n\nLED Check\n")
#    write("20008000", "0")
#    for i in range(0, 3):
#        write("20004000", "ff")
#        time.sleep(1)
#        write("20004000", "0")
#        time.sleep(1)
#
#    print("\n\nBlink Check\n")
#    write("20000004", "17d7840")
#    write("20008000", "1aa")
#    time.sleep(3)
#    write("20008000", "0")
#    write("20004000", "0")

#    print("\n\nAudio Check\n")
#    write("21100000", "0")
#    write("21100004", "80075300")

#    for i in range(256):
#        write(hex(i*4)[2:], hex(i*65536)[2:])
#    with open('music.txt', 'r') as f:
#        lines = f.readlines()
#        for i in range(480000):
#            write(hex(i*4)[2:], lines[i].strip())

    write("22000000", "0")
    write("22000010", "00600000")
    write("22000018", "00FF0000")
    write("22000004", "400")
    write("22000000", "1")
   # read("22000000")
    #read("22000010")
    #read("22000018")
    #read("22000004")
    #read("22004004")
    #read("2200400C")
    #read("22004014")
    #read("2200401C")
    #read("22004024")
    #read("2200402C")
    #read("22004034")
    #read("2200403C")
