SECTIONS
{
    . = 0x80000000;
    .text : {
        * (.start);
        * (.text);
    }
}