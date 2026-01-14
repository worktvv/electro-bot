# 🚀 Deployment Guide

This guide covers deploying Electro Bot to Railway.

## Railway Deployment

[Railway](https://railway.app) is a modern cloud platform that makes deployment simple and fast.

### Prerequisites

- GitHub account
- Railway account (sign up at [railway.app](https://railway.app))
- Your bot token from [@BotFather](https://t.me/BotFather)

### Step 1: Prepare Your Repository

1. Push your code to GitHub:
   ```bash
   git init
   git add .
   git commit -m "Initial commit"
   git remote add origin https://github.com/your-username/electro-bot.git
   git push -u origin main
   ```

2. Make sure your repository includes:
   - `Dockerfile`
   - `pom.xml`
   - Source code in `src/`

### Step 2: Create Railway Project

1. Go to [Railway Dashboard](https://railway.app/dashboard)
2. Click **"New Project"**
3. Select **"Deploy from GitHub repo"**
4. Authorize Railway to access your GitHub
5. Select your `electro-bot` repository

### Step 3: Add PostgreSQL Database

1. In your Railway project, click **"+ New"**
2. Select **"Database"** → **"Add PostgreSQL"**
3. Railway will automatically create a PostgreSQL instance
4. The `DATABASE_URL` variable will be automatically available

### Step 4: Configure Environment Variables

1. Click on your service (the one with your code)
2. Go to **"Variables"** tab
3. Add the following variables:

| Variable | Value |
|----------|-------|
| `BOT_TOKEN` | Your Telegram bot token |
| `BOT_USERNAME` | Your bot username (without @) |
| `DATABASE_URL` | `${{Postgres.DATABASE_URL}}` (Railway reference) |

> **Note**: For `DATABASE_URL`, you can use Railway's variable reference syntax to automatically link to your PostgreSQL instance.

### Step 5: Deploy

1. Railway will automatically detect the `Dockerfile` and start building
2. Wait for the build to complete (usually 2-3 minutes)
3. Check the **"Deployments"** tab for build logs
4. Once deployed, your bot should be online!

### Step 6: Verify Deployment

1. Open Telegram and find your bot
2. Send `/start` command
3. The bot should respond with a welcome message

## Monitoring

### View Logs

1. Go to your Railway project
2. Click on your service
3. Go to **"Deployments"** tab
4. Click on the active deployment
5. View real-time logs

### Check Database

1. Click on your PostgreSQL service
2. Go to **"Data"** tab to view tables
3. Or use **"Connect"** tab to get connection details

## Troubleshooting

### Bot Not Responding

1. Check deployment logs for errors
2. Verify environment variables are set correctly
3. Ensure `BOT_TOKEN` is valid

### Database Connection Issues

1. Verify `DATABASE_URL` is correctly referenced
2. Check PostgreSQL service is running
3. Look for connection errors in logs

### Build Failures

1. Check build logs for Maven errors
2. Ensure `pom.xml` is valid
3. Verify Java version compatibility (Java 17)

## Updating the Bot

Railway automatically redeploys when you push to your connected branch:

```bash
git add .
git commit -m "Update feature"
git push origin main
```

Railway will detect the push and start a new deployment.

## Cost Considerations

Railway offers:
- **Free tier**: $5 credit/month (enough for small bots)
- **Hobby plan**: $5/month for more resources
- **Pro plan**: For production workloads

The bot typically uses minimal resources and fits within the free tier for personal use.

## Alternative: Manual Deployment

If you prefer not to use GitHub integration:

1. Install Railway CLI:
   ```bash
   npm install -g @railway/cli
   ```

2. Login and link project:
   ```bash
   railway login
   railway link
   ```

3. Deploy:
   ```bash
   railway up
   ```

---

For more information, see [Railway Documentation](https://docs.railway.app/).

