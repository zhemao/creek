# Instruction Set

## sets b a v

Encoding: 0bbaaaaavvvvvvvv

Sets a byte in a scalar register. The operand `b` is the byte position
(indexed little-endian), the operand `a` is the address of the scalar register,
and the operand `v` is the byte to write.

## load r

Encoding: 10000rrr00000000

Load from memory into the vector register `r`. You must setup the start address
and counts of the memory controller and destination register before issuing
this instruction.

## store r

Encoding: 10001rrr00000000

Store to memory from the vector register `r`.

## copys r

Encoding: 10010rrr00000000

Copy the scalar value for register `r` to each word in `r`.

## addv a b r

Encoding: 10100aaabbbrrr00

Vector-wise floating-point addition of floats in `a` to floats in `b`,
storing the results in `r`.

## adds a r

Encoding: 10101aaa000rrr00

Floating-point addition of the scalar value of `a` to each vector value in `a`,
storing the result in `r`.

## subv a b r

Encoding: 10110aaabbbrrr00

Vector-wise floating-point subtraction of floats in `a` and floats in `b`
(a - b), storing the results in `r`.

## subv a r

Encoding: 10111aaa000rrr00

Subtract each float in `a` from its scalar value and store the results in `r`.

## multv a b r

Encoding: 11000aaabbbrrr00

Vector-wise floating point multiplication of floats in `a` to floats in `b`,
storing the result in `r`.

## mults a r

Encoding: 11001aaa000rrr00

Multiply scalar value of `a` to each float in `a` and store the results in `r`.

## square a r

Encoding: 11010aaa000rrr00

Multiply each float in `a` to itself and store the results in `r`.

## wait r

Encoding: 11100rrr00000000

Wait for all operations on the register `r` to complete before continuing to
the next instruction. If you use the value "0" for `r`, the co-processor will
wait indefinitely until it is reset by the application processor. This is the
primary way to return control to the application processor.

Note: If you want to write to a register immediately after an operation which
reads from the register and the read operation is slower (i.e. a `store`
instruction), you will need to insert a wait instruction in between, otherwise
the writing instruction may overwrite the values the reading instruction is
using. You will also need to do this if the read is before the write and the
read is faster, or else the read may end up accessing values that have not
yet been written.
