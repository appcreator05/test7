import express from 'express';
import path from 'path';
import fs from 'fs';
import { handleProxyRequest } from './server/proxyHandler.ts';
import { fetchRealYouTubeVideos, fetchYouTubeMoreVideos } from './server/youtubeSearch.ts';

async function startServer() {
  const app = express();
  const PORT = 3000;
  const rootDir = process.cwd();

  app.use(express.json({ limit: '10mb' }));

  // Health check endpoint
  app.get('/api/health', (req, res) => {
    res.json({ status: 'ok' });
  });

  // AI Motion Prompt Enhancer (Gemini 3.8 Flash - Free Tier)
  app.post('/api/ai/enhance-prompt', async (req, res) => {
    try {
      const { userPrompt, style } = req.body;
      const { GoogleGenAI } = await import('@google/genai');
      const ai = new GoogleGenAI();
      const response = await ai.models.generateContent({
        model: 'gemini-3.8-flash',
        contents: `You are an expert Hollywood cinematic director and AI video animation specialist. 
Given this user motion idea: "${userPrompt || 'Cinematic dramatic motion'}" and motion style: "${style || 'cinematic'}",
generate an enhanced, descriptive cinematic motion prompt (in 2-3 sentences) detailing:
1. Camera movement (e.g. slow push-in, parallax pan, low-angle tilt)
2. Atmospheric effects (e.g. volumetric lighting, mist, rain particles, golden hour lens flare)
3. Subject micro-movements and cinematic depth.
Keep the prompt concise, evocative, and tailored for generating an animated video from a still image.
Only return the prompt text without any introductory conversational text or quotes.`,
      });
      const enhanced = response.text?.trim() || userPrompt;
      res.json({ success: true, enhancedPrompt: enhanced });
    } catch (err: any) {
      console.error('Enhance prompt error:', err);
      res.json({ 
        success: true, 
        enhancedPrompt: `${req.body.userPrompt || 'Cinematic slow zoom with depth of field'}, dramatic lighting with gentle atmospheric particle drift and realistic camera glide.` 
      });
    }
  });

  // Real YouTube Search endpoint for the Movie App
  app.get('/api/youtube/search', async (req, res) => {
    try {
      const queryParam = req.query.query as string | undefined;
      const data = await fetchRealYouTubeVideos(queryParam);
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
      res.json(data);
    } catch (err: any) {
      res.status(500).json({
        success: false,
        error: err?.message || 'Server error fetching YouTube results',
      });
    }
  });

  // Infinite scroll endpoint for loading more YouTube movies
  app.get('/api/youtube/more', async (req, res) => {
    try {
      const token = req.query.token as string | undefined;
      const apiKey = req.query.apiKey as string | undefined;
      const clientVersion = req.query.clientVersion as string | undefined;

      if (!token) {
        return res.status(400).json({ success: false, error: 'Continuation token is required' });
      }

      const data = await fetchYouTubeMoreVideos(token, apiKey, clientVersion);
      res.setHeader('Cache-Control', 'no-cache, no-store, must-revalidate');
      res.json(data);
    } catch (err: any) {
      res.status(500).json({
        success: false,
        error: err?.message || 'Server error fetching more videos',
      });
    }
  });

  // API endpoints for WebView proxy and diagnostics
  app.all('/api/proxy', async (req, res) => {
    await handleProxyRequest(req, res);
  });

  app.get('/api/info', async (req, res) => {
    await handleProxyRequest(req, res);
  });

  // Intercept any direct Blogger paths (posts, search, labels, feeds)
  app.all(/^\/(20\d\d|search|p|feeds|favicon\.ico).*/, async (req, res) => {
    await handleProxyRequest(req, res);
  });

  // Serve public assets (such as ad video sample)
  app.use(express.static(path.join(rootDir, 'public')));
  app.get('/ad-video.mp4', (req, res) => {
    const videoPath = path.join(rootDir, 'public', 'ad-video.mp4');
    if (fs.existsSync(videoPath)) {
      res.sendFile(videoPath);
    } else {
      res.status(404).send('Ad video placeholder');
    }
  });

  // Vite middleware for development vs static build for production
  if (process.env.NODE_ENV !== 'production') {
    const { createServer: createViteServer } = await import('vite');
    const vite = await createViteServer({
      server: { middlewareMode: true },
      appType: 'spa',
    });
    app.use(vite.middlewares);
  } else {
    const distPath = path.join(rootDir, 'dist');
    app.use(express.static(distPath));
    app.get('*', (req, res) => {
      res.sendFile(path.join(distPath, 'index.html'));
    });
  }

  app.listen(PORT, '0.0.0.0', () => {
    console.log(`HDSKay WebView server running on http://0.0.0.0:${PORT}`);
  });
}

startServer();
