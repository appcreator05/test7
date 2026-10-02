export interface AndroidStartAppBridge {
  showRewardedVideo?: () => void;
  showRewardedVideoForUrl?: (url: string) => void;
  showInterstitial?: () => void;
  isNativeApp?: () => boolean;
  openYouTubeMovieSection?: () => void;
  openCustomTab?: (url: string) => void;
  setBannerVisibility?: (visible: boolean) => void;
  setVideoFullscreen?: (isFs: boolean) => void;
}

declare global {
  interface Window {
    AndroidStartApp?: AndroidStartAppBridge;
    Android?: AndroidStartAppBridge;
    onRewardedVideoClosed?: () => void;
    openYouTubeMovieSection?: () => void;
  }
}

export type AdType = 'rewarded' | 'interstitial' | 'banner';
