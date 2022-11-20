void __attribute__ ((interrupt("machine"), weak, alias("riscv_nop_machine"))) riscv_mtvec_msi(void); 
void __attribute__ ((interrupt("machine"), weak, alias("riscv_nop_machine"))) riscv_mtvec_mei(void);
void __attribute__ ((interrupt("machine"), weak, alias("riscv_nop_machine"))) riscv_mtvec_mti(void);

static void __attribute__ ((interrupt("machine"))) riscv_nop_machine(void) {
    // Nop machine mode interrupt.
}