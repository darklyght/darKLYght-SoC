int main(void) {
    int* read_data = (int*)0x40000000;
    int* read_info = (int*)0x40000010;
    int* write_data = (int*)0x40000020;
    int* write_info = (int*)0x40000030;
    int data;
    while (1) {
        if (*read_info & 0x80000000) {
            data = *read_data & 0xff;
            *write_data = data;
        }
    }
    return 0;
}