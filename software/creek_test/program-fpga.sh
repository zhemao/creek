if [ -z "$1" ]; then
    echo "Usage: $0 {sockit}"
    exit 1
fi

BOARD="$1"
SOF_FILE="../../fpga/${BOARD}/output_files/creek_${BOARD}.sof"

nios2-configure-sof "$SOF_FILE"
