package ds.dsh.DeepSeek;

/**
 * Stable Xposed entry for the ds.dsh.DeepSeek application id.
 *
 * The implementation remains in the existing com.dsmod.probe namespace to avoid a risky
 * all-at-once rewrite of the large hook codebase. This public entry shim preserves the exact
 * legacy IXposedHookLoadPackage contract and delegates to the existing implementation.
 */
public final class Main extends com.dsmod.probe.Main {
    public Main() {
        super();
    }
}
