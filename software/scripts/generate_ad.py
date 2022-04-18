with open('ad.txt', 'w+') as f:
    for i in range(0, 1024, 4):
        f.write('{:0>4X}'.format(i%65536))
        f.write('{:0>4X}'.format(i%65536))
        f.write('{:0>4X}'.format((i+1)%65536))
        f.write('{:0>4X}'.format((i+1)%65536))
        f.write('{:0>4X}'.format((i+2)%65536))
        f.write('{:0>4X}'.format((i+2)%65536))
        f.write('{:0>4X}'.format((i+3)%65536))
        f.write('{:0>4X}'.format((i+3)%65536))
        f.write('\n')
