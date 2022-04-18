import wave
import struct

def oneChannel(fname, chanIdx):
    f = wave.open(fname, 'rb')
    chans = f.getnchannels()
    samps = f.getnframes()
    sampwidth = f.getsampwidth()
    assert sampwidth == 2
    s = f.readframes(samps) #read the all the samples from the file into a byte string
    f.close()
    unpstr = '<{0}h'.format(samps*chans) #little-endian 16-bit samples
    x = list(struct.unpack(unpstr, s)) #convert the byte string into a list of ints
    return x[chanIdx::chans] #return the desired channel

c0 = oneChannel('music.wav', 0)
c1 = oneChannel('music.wav', 1)

with open('music.txt', 'w+') as f:
    for i in range(0, len(c0), 4):
        print('{:.2f}%'.format(float(i) / float(len(c0)) * 100), end='\r')
        f.write('{:04X}'.format(c1[i] + 32768))
        f.write('{:04X}'.format(c0[i] + 32768))
        try:
            f.write('{:04X}'.format(c1[i+1] + 32768))
            f.write('{:04X}'.format(c0[i+1] + 32768))
        except IndexError:
            f.write('{:04X}'.format(32768))
            f.write('{:04X}'.format(32768))
        try:
            f.write('{:04X}'.format(c1[i+2] + 32768))
            f.write('{:04X}'.format(c0[i+2] + 32768))
        except IndexError:
            f.write('{:04X}'.format(32768))
            f.write('{:04X}'.format(32768))
        try:
            f.write('{:04X}'.format(c1[i+3] + 32768))
            f.write('{:04X}'.format(c0[i+3] + 32768))
        except IndexError:
            f.write('{:04X}'.format(32768))
            f.write('{:04X}'.format(32768))
        f.write('\n')
    print('\n')
