BSP_DIR="../creek_bsp"
ELF_NAME="creek_test.elf"

nios2-app-generate-makefile --src-dir . --bsp-dir "$BSP_DIR" \
    --elf-name "$ELF_NAME"
