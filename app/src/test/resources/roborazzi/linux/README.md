These are unmodified Linux actual captures from GitHub CI run **41109957914**, Play job
**124249503817**, commit **3faa55fec**. Their matching historical Mac references are stored in
`../mac`, copied byte-for-byte from committed **6a1b0381b**. These pairs are frozen comparator
regression inputs, not the current visual goldens in `src/test/screenshots`.

All five pairs have identical dimensions and alpha. Maximum per-channel RGB difference is 2/255.
The test-only comparator applies that bound only to these five named fixtures and requires zero
pixels outside it. Dimension and alpha changes remain exact failures. Mutation tests exercise
single-pixel RGB/alpha changes, text/layout movement and changed status fills against these inputs.
The twelve other design-system fixtures retain the default comparator.

Keep these historical pairs unchanged when intentionally redesigning the current UI. Review and
refresh current visual goldens independently; a new UI does not invalidate the old numerical
rounding regression. Add separate measured pairs only when extending the comparator's contract.
