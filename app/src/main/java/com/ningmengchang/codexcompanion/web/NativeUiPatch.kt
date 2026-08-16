package com.ningmengchang.codexcompanion.web

object NativeUiPatch {
    val script: String = """
        (() => {
          const activeViewName = () => document.querySelector('.view.active')?.dataset?.view || null;
          const openDialogs = () => Array.from(document.querySelectorAll('dialog[open]'));
          const state = window.__codexNativeNavigationState || {
            currentView: activeViewName(),
            viewStack: [],
            suppressTarget: null,
          };
          window.__codexNativeNavigationState = state;

          const hideWebFullscreenButton = () => {
            const button = document.getElementById('fullscreenButton');
            if (!button) return;
            button.hidden = true;
            button.tabIndex = -1;
            button.setAttribute('aria-hidden', 'true');
            button.style.setProperty('display', 'none', 'important');
          };

          const installConnectionSettingsEntry = () => {
            if (document.getElementById('nativeConnectionSettingsButton')) return;
            if (!window.CodexNativeUi || typeof window.CodexNativeUi.openConnectionSettings !== 'function') return;
            const body = document.querySelector('#settingsSheet .settings-body');
            if (!body) return;
            const button = document.createElement('button');
            button.id = 'nativeConnectionSettingsButton';
            button.type = 'button';
            button.className = 'thread-action-row';
            button.textContent = 'SSH 连接设置';
            button.title = '修改电脑地址、端口和登录方式';
            button.addEventListener('click', () => {
              const dialog = document.getElementById('settingsSheet');
              if (dialog?.open && typeof dialog.close === 'function') dialog.close();
              try {
                window.CodexNativeUi.openConnectionSettings();
              } catch (_) {}
            });
            body.append(button);
          };

          const recordActiveView = () => {
            const active = activeViewName();
            if (!active || active === state.currentView) return;
            if (state.suppressTarget === active) {
              state.suppressTarget = null;
            } else if (state.currentView) {
              if (state.viewStack[state.viewStack.length - 1] !== state.currentView) {
                state.viewStack.push(state.currentView);
                if (state.viewStack.length > 32) state.viewStack.shift();
              }
            }
            state.currentView = active;
          };

          const goToView = (target) => {
            const button = Array.from(document.querySelectorAll('.bottom-nav button[data-tab]'))
              .find((candidate) => candidate.dataset?.tab === target);
            if (!button) return false;
            state.suppressTarget = target;
            button.click();
            recordActiveView();
            if (activeViewName() !== target) {
              state.suppressTarget = null;
              return false;
            }
            return true;
          };

          const closeTopDialog = () => {
            const dialogs = openDialogs();
            const dialog = dialogs[dialogs.length - 1];
            if (!dialog || typeof dialog.close !== 'function') return false;
            dialog.close();
            return true;
          };

          const closeFilePopover = () => {
            const popover = document.getElementById('fileUploadPopover');
            if (!popover || popover.hidden) return false;
            popover.hidden = true;
            document.getElementById('fileDirectoryButton')?.setAttribute('aria-expanded', 'false');
            return true;
          };

          const leaveFullscreen = () => {
            if (!document.fullscreenElement && !document.webkitFullscreenElement) return false;
            const exit = document.exitFullscreen || document.webkitExitFullscreen;
            if (typeof exit === 'function') exit.call(document);
            return true;
          };

          const goUpDirectory = () => {
            if (activeViewName() !== 'projects') return false;
            const button = document.getElementById('projectUpButton');
            if (!button || button.hidden || button.disabled) return false;
            button.click();
            return true;
          };

          const goToPreviousView = () => {
            recordActiveView();
            while (state.viewStack.length) {
              const target = state.viewStack.pop();
              if (target && target !== state.currentView && goToView(target)) return true;
            }
            if (state.currentView && state.currentView !== 'chat') return goToView('chat');
            return false;
          };

          window.__codexNativeUiBack = () => (
            closeTopDialog()
            || closeFilePopover()
            || leaveFullscreen()
            || goUpDirectory()
            || goToPreviousView()
          );
          window.__codexNativeUiRefresh = () => {
            hideWebFullscreenButton();
            installConnectionSettingsEntry();
            recordActiveView();
          };

          if (window.__codexNativeUiPatched) {
            window.__codexNativeUiRefresh();
            return;
          }
          window.__codexNativeUiPatched = true;

          const viewObserver = new MutationObserver((mutations) => {
            if (mutations.some((mutation) => mutation.target?.classList?.contains('view'))) {
              recordActiveView();
            }
          });
          viewObserver.observe(document.getElementById('app') || document.documentElement, {
            attributes: true,
            attributeFilter: ['class'],
            subtree: true,
          });

          window.__codexNativeUiRefresh();
        })();
    """.trimIndent()

    val handleBackScript: String = """
        (() => {
          try {
            return Boolean(
              typeof window.__codexNativeUiBack === 'function'
              && window.__codexNativeUiBack()
            );
          } catch (_) {
            return false;
          }
        })();
    """.trimIndent()
}
