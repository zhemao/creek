# Registers

The Creek co-processor contains 7 vector registers, each of which holds
256 128-bit quadwords. The vector registers can only be accessed by the
functional units, and the entries in the vector registers can only be
accessed in sequential order. The vector registers are labeled 1-7.
Register 0 is a dummy register which has some special properties.

The co-processor also contains 32 32-bit scalar registers. The first four
registers are associated with the memory controller. The rest are associated
with the vector registers in groups of four.

Scalar register 0 in each group is the start position. When an operation is
triggered for the corresponding vector register, the address will be set back
to this position.

Scalar register 2 in each group is the count. When an operation is triggered
for the corresponding vector register, it will stop after processing that
many quadwords.

Scalar register 3 in each group is the scalar value. This is the floating-point
value used as the scalar in the "adds", "mults", and "copys" instructions.

# Floating-point Units

The computational units of the co-processor are streaming floating-point
addition and multiplication units. These units contain four lanes, and so each
stage of the pipeline can process four IEEE 754 single-precision floats.

# Memory Controller

The memory controller has a 30-bit address range. However, it can only access
memory in 128-bit quadwords and has no support for unaligned accesses.
The memory controller is implemented as an Avalon-MM master, so you can add
the Creek co-processor as an Avalon component in your Qsys designs.
