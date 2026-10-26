CFLAGS ?= -O2 -std=c11 -Wall -Wextra -Werror

oj-runner: oj-runner.c
	$(CC) $(CFLAGS) -static -o $@ $<

clean:
	rm -f oj-runner

.PHONY: clean
