// Many constant evaluations, each below GCC's per-evaluation limit, that together take far longer than the
// compilation time limit.
constexpr long long slow(int seed) {
    long long s = 0;
    for (int i = 0; i < 1500; ++i)
        for (int j = 0; j < 1500; ++j) s += (i ^ j) + seed;
    return s;
}
constexpr long long v0 = slow(0);
constexpr long long v1 = slow(1);
constexpr long long v2 = slow(2);
constexpr long long v3 = slow(3);
constexpr long long v4 = slow(4);
constexpr long long v5 = slow(5);
constexpr long long v6 = slow(6);
constexpr long long v7 = slow(7);
constexpr long long v8 = slow(8);
constexpr long long v9 = slow(9);
constexpr long long v10 = slow(10);
constexpr long long v11 = slow(11);
constexpr long long v12 = slow(12);
constexpr long long v13 = slow(13);
constexpr long long v14 = slow(14);
constexpr long long v15 = slow(15);
constexpr long long v16 = slow(16);
constexpr long long v17 = slow(17);
constexpr long long v18 = slow(18);
constexpr long long v19 = slow(19);
constexpr long long v20 = slow(20);
constexpr long long v21 = slow(21);
constexpr long long v22 = slow(22);
constexpr long long v23 = slow(23);
constexpr long long v24 = slow(24);
constexpr long long v25 = slow(25);
constexpr long long v26 = slow(26);
constexpr long long v27 = slow(27);
constexpr long long v28 = slow(28);
constexpr long long v29 = slow(29);
constexpr long long v30 = slow(30);
constexpr long long v31 = slow(31);
constexpr long long v32 = slow(32);
constexpr long long v33 = slow(33);
constexpr long long v34 = slow(34);
constexpr long long v35 = slow(35);
constexpr long long v36 = slow(36);
constexpr long long v37 = slow(37);
constexpr long long v38 = slow(38);
constexpr long long v39 = slow(39);
int main() { return (v0 + v1 + v2 + v3 + v4 + v5 + v6 + v7 + v8 + v9 + v10 + v11 + v12 + v13 + v14 + v15 + v16 + v17 + v18 + v19 + v20 + v21 + v22 + v23 + v24 + v25 + v26 + v27 + v28 + v29 + v30 + v31 + v32 + v33 + v34 + v35 + v36 + v37 + v38 + v39) == 0; }
