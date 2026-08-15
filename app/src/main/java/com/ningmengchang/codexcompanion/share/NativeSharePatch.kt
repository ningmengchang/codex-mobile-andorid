package com.ningmengchang.codexcompanion.share

object NativeSharePatch {
    val script: String = """
        (() => {
          if (window.__codexNativeSharePatched || !window.CodexNativeShare) return;
          window.__codexNativeSharePatched = true;

          const originalFetch = window.fetch.bind(window);
          const originalShare = typeof navigator.share === 'function' ? navigator.share.bind(navigator) : null;
          const originalCanShare = typeof navigator.canShare === 'function' ? navigator.canShare.bind(navigator) : null;
          const pending = new Map();
          let nativeSource = null;

          const mimeFromName = (name) => {
            const extension = String(name || '').split('.').pop().toLowerCase();
            const types = {
              pdf: 'application/pdf', ppt: 'application/vnd.ms-powerpoint',
              pptx: 'application/vnd.openxmlformats-officedocument.presentationml.presentation',
              doc: 'application/msword',
              docx: 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
              xls: 'application/vnd.ms-excel',
              xlsx: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
              zip: 'application/zip', md: 'text/markdown', txt: 'text/plain',
              png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif'
            };
            return types[extension] || 'application/octet-stream';
          };

          const isNativeShareFetch = (url, init) => {
            const dialog = document.getElementById('fileShareDialog');
            const method = String((init && init.method) || 'GET').toUpperCase();
            return method === 'GET' && dialog && dialog.open &&
              /^\/api\/artifacts\/[^/]+\/raw$/.test(url.pathname);
          };

          window.fetch = (input, init) => {
            try {
              const raw = typeof input === 'string' ? input : input.url;
              const url = new URL(raw, location.href);
              if (isNativeShareFetch(url, init)) {
                const download = document.getElementById('shareDownloadButton');
                const name = (download && download.download) || 'Codex-文件';
                const mime = mimeFromName(name);
                nativeSource = { url: url.href, name: name, mime: mime };
                return Promise.resolve(new Response(new Blob([], { type: mime }), {
                  status: 200,
                  headers: { 'content-type': mime, 'content-length': '0' }
                }));
              }
            } catch (_) {}
            return originalFetch(input, init);
          };

          const nativeCanShare = (data) => {
            if (data && data.files && data.files.length) return true;
            return originalCanShare ? originalCanShare(data) : false;
          };

          const nativeShare = (data) => {
            if (!data || !data.files || !data.files.length || !nativeSource) {
              if (originalShare) return originalShare(data);
              return Promise.reject(new DOMException('没有可分享的文件', 'NotAllowedError'));
            }
            const file = data.files[0];
            const requestId = String(Date.now()) + '-' + Math.random().toString(36).slice(2);
            return new Promise((resolve, reject) => {
              pending.set(requestId, { resolve: resolve, reject: reject });
              try {
                window.CodexNativeShare.shareFile(
                  nativeSource.url,
                  String(file.name || nativeSource.name),
                  String(file.type || nativeSource.mime),
                  requestId
                );
              } catch (error) {
                pending.delete(requestId);
                reject(error);
              }
            });
          };

          try { Object.defineProperty(navigator, 'canShare', { configurable: true, value: nativeCanShare }); }
          catch (_) { navigator.canShare = nativeCanShare; }
          try { Object.defineProperty(navigator, 'share', { configurable: true, value: nativeShare }); }
          catch (_) { navigator.share = nativeShare; }

          window.__codexNativeShareResolve = (requestId) => {
            const item = pending.get(requestId);
            if (!item) return;
            pending.delete(requestId);
            nativeSource = null;
            item.resolve();
          };
          window.__codexNativeShareReject = (requestId, message) => {
            const item = pending.get(requestId);
            if (!item) return;
            pending.delete(requestId);
            item.reject(new DOMException(message || '原生分享失败', 'NotAllowedError'));
          };
          window.__codexNativeShareProgress = (message) => {
            const status = document.getElementById('fileShareStatus');
            if (status) {
              status.textContent = message;
              status.dataset.tone = 'loading';
            }
          };
        })();
    """.trimIndent()
}
