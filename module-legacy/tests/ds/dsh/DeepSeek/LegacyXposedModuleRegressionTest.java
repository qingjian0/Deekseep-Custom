package ds.dsh.DeepSeek;

import java.lang.reflect.Member;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Bridges an already-authorized feature-specific ROI candidate into lifecycle management. */
final class RoiManagedHookInstaller {
    interface Authorization { boolean isAuthorized() throws Throwable; }
    private static final HookLifecycleManager LIFECYCLE = new HookLifecycleManager();
    private RoiManagedHookInstaller() {}

    static HookLifecycleManager.Status install(String targetId, LegacyXposedModule adapter,
            Member target, LegacyXposedModule.Hooker hooker, Authorization authorization) {
        if (targetId == null || targetId.trim().isEmpty())
            throw new IllegalArgumentException("stable target ID is required");
        if (adapter == null || target == null || hooker == null || authorization == null)
            throw new IllegalArgumentException("adapter, target, hooker, and authorization are required");
        try {
            if (!authorization.isAuthorized()) return new HookLifecycleManager.Status(targetId,
                    HookLifecycleManager.State.SKIPPED, 0, false,
                    "roi_authorization_denied_before_install");
        } catch (Throwable error) {
            if (error instanceof VirtualMachineError) throw (VirtualMachineError) error;
            if (error instanceof ThreadDeath) throw (ThreadDeath) error;
            return new HookLifecycleManager.Status(targetId, HookLifecycleManager.State.SKIPPED,
                    0, false, "roi_authorization_check_failed:" + error.getClass().getName());
        }
        final AtomicReference<LegacyXposedModule.HookRegistrationHandle> registration =
                new AtomicReference<LegacyXposedModule.HookRegistrationHandle>();
        HookLifecycleManager.Spec spec = new HookLifecycleManager.Spec(targetId,
                Collections.<String>emptySet(), Collections.<String>emptySet(), true, false);
        return LIFECYCLE.install(spec, new HookLifecycleManager.Installer() {
            @Override public HookLifecycleManager.Unhook install(HookLifecycleManager.Spec ignored) {
                final LegacyXposedModule.HookRegistrationHandle handle =
                        adapter.registerManaged(target, hooker);
                registration.set(handle);
                return new HookLifecycleManager.Unhook() {
                    @Override public void uninstall() { handle.unhook(); }
                };
            }
        }, new HookLifecycleManager.Verifier() {
            @Override public boolean verify(HookLifecycleManager.Spec ignored) throws Throwable {
                LegacyXposedModule.HookRegistrationHandle handle = registration.get();
                return handle != null && handle.isActive() && authorization.isAuthorized();
            }
        });
    }

    static HookLifecycleManager.Status uninstall(String id) { return LIFECYCLE.uninstall(id); }
    static HookLifecycleManager.Status status(String id) { return LIFECYCLE.status(id); }
    static List<HookLifecycleManager.Status> snapshot() { return LIFECYCLE.snapshot(); }
    static boolean hasRequiredFailures() { return LIFECYCLE.hasRequiredFailures(); }
}