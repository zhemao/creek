#include <stdio.h>
#include <string.h>
#include <stdlib.h>

#include "creek.h"
#include "instructions.h"

int main()
{
	struct creek creek[1];
	float avalues[4] = {1.0f, 2.34e12f, 31.93f, 5.7f};
	float bvalues[4] = {1.0f, 1.0e-4f, 2.0f, 0.5f};
	float expected[4] = {1.0f, 2.34e8f, 63.86f, 2.85f};
	float results[4];

	float *d_avalues, *d_bvalues, *d_results;
	int i, res;

	uint32_t expbits;
	uint32_t resbits;

	res = creek_init(creek);
	if (res < 0) {
		perror("creek_init");
		return -1;
	}

	d_avalues = (float *) creek_malloc(creek, sizeof(avalues));
	if (d_avalues == NULL) {
		fprintf(stderr, "Too big: %u\n", creek->last_mem_pos);
		creek_release(creek);
		exit(EXIT_FAILURE);
	}
	d_bvalues = (float *) creek_malloc(creek, sizeof(bvalues));
	if (d_bvalues == NULL) {
		fprintf(stderr, "Too big: %u\n", creek->last_mem_pos);
		creek_release(creek);
		exit(EXIT_FAILURE);
	}
	d_results = (float *) creek_malloc(creek, sizeof(results));
	if (d_results == NULL) {
		fprintf(stderr, "Too big: %u\n", creek->last_mem_pos);
		creek_release(creek);
		exit(EXIT_FAILURE);
	}

	memcpy(d_avalues, avalues, sizeof(avalues));
	memcpy(d_bvalues, bvalues, sizeof(bvalues));

	set_scalar_int(creek, 0, REG_COUNT, 1);
	creek_load_reg(creek, 1, d_avalues, 4);
	creek_write_instr(creek, wait_instr(1));
	creek_store_reg(creek, 1, d_results, 4);
	creek_run_and_sync(creek);

	memcpy(results, d_results, sizeof(results));

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

	creek_release(creek);

	printf("Program finished\n");

	return 0;
}
