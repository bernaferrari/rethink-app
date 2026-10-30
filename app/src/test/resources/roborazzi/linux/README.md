These are unmodified Linux actual captures from GitHub CI run **41109957914**, Play job
**124249503817**, commit **3faa55fec**. They are comparator regression inputs, not alternate goldens.
The reviewed Mac references remain in `src/test/screenshots`, unchanged.

All five captures have identical dimensions and alpha to their references. Maximum per-channel
RGB difference is 2/255. The test-only comparator applies that bound only to these five named
fixtures and requires zero pixels outside it. Dimension and alpha changes remain exact failures.
The twelve other design-system fixtures retain the default comparator. Mutation tests exercise
single-pixel RGB/alpha changes, text/layout movement and changed status fills against these inputs.

Intentional changes to these five visuals require recapturing and reviewing matching Linux inputs
alongside refreshed references; the archived CI inputs otherwise correctly fail that comparison.
