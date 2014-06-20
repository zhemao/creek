#include <stdio.h>
#include <system.h>

#include "creek.h"
#include "instructions.h"
#include <string.h>

int main()
{
	struct creek creek[1];
	float avalues[4] = {1.0f, 2.34e12f, 31.93f, 5.7f};
	float bvalues[4] = {1.0f, 1.0e-4f, 2.0f, 0.5f};
	float expected[4] = {1.0f, 2.34e8f, 63.86f, 2.85f};
	float results[4];
	int i;

	uint32_t expbits;
	uint32_t resbits;

	creek_init(creek);

	creek_load_reg(creek, 1, avalues, 4);
	creek_write_instr(creek, wait_instr(1));
	creek_store_reg(creek, 1, results, 4);
	creek_run_and_sync(creek);

	for (i = 0; i < 4; i++) {
		memcpy(&expbits, &avalues[i], sizeof(float));
		memcpy(&resbits, &results[i], sizeof(float));
		if (expbits != resbits) {
			printf("Error, expected %f but got %f\n",
					avalues[i], results[i]);
		}
	}

	/*creek_load_reg(creek, 1, avalues, 4);
	creek_load_reg(creek, 2, bvalues, 4);
	creek_write_instr(creek, wait_instr(1));
	creek_write_instr(creek, wait_instr(2));
	creek_write_instr(creek, multv_instr(1, 2, 3));
	creek_store_reg(creek, 3, results, 4);
	creek_write_instr(creek, wait_instr(3));

	printf("Starting co-processor and waiting for it to finish\n");
	creek_run_and_sync(creek);

	for (i = 0; i < 4; i++) {
		memcpy(&expbits, &expected[i], sizeof(float));
		memcpy(&resbits, &results[i], sizeof(float));
		if (expbits != resbits) {
			printf("Error, expected %f but got %f\n",
					expected[i], results[i]);
		}
	}*/

	printf("Program finished\n");

	return 0;
}
