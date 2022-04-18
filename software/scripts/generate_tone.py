import math

frequency = 440
sample_rate = 48000
c0 = []

for i in range(0, 1000000):
    c0.append(int(32768 * math.sin((2 * math.pi * frequency * i) / sample_rate)) + 32768)

print(c0)

with open('tone.txt', 'w+') as f:
    for i in range(0, len(c0), 4):
        f.write('{:04X}'.format(c0[i] % 65536))
        f.write('{:04X}'.format(c0[i] % 65536))
        f.write('{:04X}'.format(c0[i+1] % 65536))
        f.write('{:04X}'.format(c0[i+1] % 65536))
        f.write('{:04X}'.format(c0[i+2] % 65536))
        f.write('{:04X}'.format(c0[i+2] % 65536))
        f.write('{:04X}'.format(c0[i+3] % 65536))
        f.write('{:04X}'.format(c0[i+3] % 65536))
        f.write('\n')
