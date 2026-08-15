package com.ningmengchang.codexcompanion.web

object NativeUiPatch {
    val script: String = """
        (() => {
          const notify = () => {
            const dialogOpen = Boolean(document.querySelector('dialog[open]'));
            try {
              if (window.CodexNativeUi && typeof window.CodexNativeUi.setDialogOpen === 'function') {
                window.CodexNativeUi.setDialogOpen(dialogOpen);
              }
            } catch (_) {}
          };
          if (window.__codexNativeUiPatched) {
            notify();
            return;
          }
          window.__codexNativeUiPatched = true;
          window.__codexNativeUiRefresh = notify;
          const observer = new MutationObserver(notify);
          observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ['open'],
            subtree: true,
          });
          notify();
        })();
    """.trimIndent()
}
