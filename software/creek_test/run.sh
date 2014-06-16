ELF_NAME=creek_test.elf

make "$ELF_NAME" || exit 1
nios2-download -g -r "$ELF_NAME" || exit 1
nios2-terminal
