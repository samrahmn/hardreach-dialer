# Get Your APK - Quick Guide

## Option 1: GitHub Actions (Recommended - FREE)

1. Create new GitHub repo: https://github.com/new
   - Name: `hardreach-dialer`  
   - Make it Public

2. Upload project files:
   ```bash
   # Download the tarball from server
   scp ubuntu@your-server:/tmp/hardreach-dialer.tar.gz .
   
   # Extract and push
   tar -xzf hardreach-dialer.tar.gz
   cd hardreach-dialer
   git remote add origin https://github.com/YOUR_USERNAME/hardreach-dialer.git
   git push -u origin main
   ```

3. Wait 2-3 minutes for GitHub Actions to build

4. Download APK:
   - Go to Actions tab
   - Click latest workflow run
   - Download `hardreach-dialer-apk` artifact
   - Extract ZIP → install APK on phone

## Option 2: Online APK Builder (Fast)

1. Go to: https://www.apk-builder.com/ or https://appbuilder.dev/
2. Upload the project ZIP
3. Click "Build APK"
4. Download APK when ready (5-10 mins)

## Option 3: Hire Developer ($50-100)

1. Post on Fiverr: "Build Android APK from source code"
2. Send them: `/tmp/hardreach-dialer.tar.gz`
3. Get APK in 24 hours

## What's Included

✅ Complete Android project (Kotlin + Gradle)
✅ Auto-dialer with conference call
✅ Background service  
✅ CRM webhook integration
✅ GitHub Actions workflow
✅ All source code ready to build

## File Location

Project tarball: `/tmp/hardreach-dialer.tar.gz` (120 KB)

Download it from your server and follow Option 1 above.
