package com.ningmengchang.codexcompanion.web

object NativeUiPatch {
    val script: String = """
        (() => {
          const activeViewName = () => document.querySelector('.view.active')?.dataset?.view || null;
          const openDialogs = () => Array.from(document.querySelectorAll('dialog[open]'));

          const hideWebFullscreenButton = () => {
            const button = document.getElementById('fullscreenButton');
            if (!button) return;
            button.hidden = true;
            button.tabIndex = -1;
            button.setAttribute('aria-hidden', 'true');
            button.style.setProperty('display', 'none', 'important');
          };

          const installLandscapeChatStyles = () => {
            if (document.getElementById('nativeLandscapeChatStyles')) return;
            const style = document.createElement('style');
            style.id = 'nativeLandscapeChatStyles';
            style.textContent = `
              @media screen and (orientation: landscape) {
                #chatView.active .bubble {
                  font-size: 12px;
                  line-height: 1.45;
                }
                #chatView.active .message.user .bubble {
                  padding: 5px 28px 5px 9px;
                  line-height: 1.4;
                }
                #chatView.active .message > .agent-card,
                #chatView.active .plan-card .tool-content.agent-card {
                  font-size: 12px;
                  line-height: 1.5;
                }
                #chatView.active .agent-card h1 { font-size: 15px; }
                #chatView.active .agent-card h2 { font-size: 14px; }
                #chatView.active .agent-card h3 { font-size: 13px; }
                #chatView.active .agent-card h4,
                #chatView.active .agent-card h5,
                #chatView.active .agent-card h6 { font-size: 12px; }
                #chatView.active .agent-card pre,
                #chatView.active .tool-card pre { font-size: 10px; }
                #chatView.active .composer {
                  padding: 4px 10px calc(4px + var(--safe-bottom));
                }
                #chatView.active .composer textarea {
                  max-height: 60px;
                  padding: 2px;
                  font-size: 11px;
                  line-height: 1.3;
                }
                #chatView.active .composer-footer { gap: 6px; }
                #chatView.active .composer-actions { gap: 5px; }
                #chatView.active .send-button {
                  width: 28px;
                  height: 28px;
                  border-radius: 9px;
                  font-size: 18px;
                }
                #scrollLatestButton {
                  left: auto;
                  right: max(12px, env(safe-area-inset-right));
                  bottom: calc(92px + var(--safe-bottom) + var(--kb-inset));
                  translate: 0;
                }
                #jumpQuestionButton {
                  left: auto;
                  right: max(12px, env(safe-area-inset-right));
                  bottom: calc(132px + var(--safe-bottom) + var(--kb-inset));
                  translate: 0;
                }
              }
            `;
            (document.head || document.documentElement).append(style);
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

          const navigateWebHistory = () => {
            if (activeViewName() === 'threads') return false;
            history.back();
            return true;
          };

          window.__codexNativeUiBack = () => (
            closeTopDialog()
            || closeFilePopover()
            || leaveFullscreen()
            || goUpDirectory()
            || navigateWebHistory()
          );
          window.__codexNativeUiRefresh = () => {
            hideWebFullscreenButton();
            installLandscapeChatStyles();
            installConnectionSettingsEntry();
          };

          if (window.__codexNativeUiPatched) {
            window.__codexNativeUiRefresh();
            return;
          }
          window.__codexNativeUiPatched = true;

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
