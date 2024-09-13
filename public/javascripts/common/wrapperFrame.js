const iframe = document.getElementById('wrapperFrame');

function scaleIframeContent() {
    iframe.contentWindow.devicePixelRatio = 1;
    const iframeDocument = iframe.contentDocument || iframe.contentWindow.document;
    const contentElement = iframeDocument.querySelector('.tool-ui');

    if (contentElement) {
        const iframeWidth = window.innerWidth;
        const iframeHeight = window.innerHeight - 70;
        const contentWidth = contentElement.clientWidth;
        const contentHeight = contentElement.clientHeight + 100; // Add 100px for padding purposes.

        const scale = Math.min(iframeWidth / contentWidth, iframeHeight / contentHeight);
        
        iframe.style.transform = `scale(${scale})`;
        iframe.style.width = `${(1 / scale) * 100}vw`;
        iframe.style.height = `calc(${(1 / scale) * 100}vh - ${(1 / scale) * 70}px)`;
    }
}

iframe.addEventListener('load', scaleIframeContent);
window.addEventListener('resize', scaleIframeContent);

iframe.src = window.location.href;