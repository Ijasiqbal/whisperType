# Force Update System - Setup Guide

## ✅ Implementation Complete

The force update system has been successfully implemented with flexible version control.

---

## 📦 What Was Implemented

### 1. **Extended RemoteConfigManager**
   - Added `UpdateConfig` data class
   - Added force update and soft update parameters
   - Parses comma-separated blocked versions list

### 2. **ForceUpdateChecker Utility**
   - Checks if version is below minimum
   - Checks if version is in blocked list
   - Supports both force and soft updates

### 3. **Update Dialogs**
   - `ForceUpdateDialog`: Non-dismissible, blocks app usage
   - `SoftUpdateDialog`: Dismissible, allows continued usage

### 4. **MainActivity Integration**
   - Checks update status on app launch
   - Shows appropriate dialog based on status
   - Integrates with existing auth flow

---

## 🔧 Firebase Console Setup

### Step 1: Open Firebase Console

Go to: **Firebase Console → Your Project → Remote Config**

### Step 2: Add These Parameters

| Parameter Key | Type | Default Value | Description |
|---------------|------|---------------|-------------|
| `force_update_enabled` | Boolean | `false` | Enable force update blocking |
| `force_update_min_version_code` | Number | `1` | Minimum version code required |
| `force_update_blocked_versions` | String | `""` | Comma-separated blocked versions (e.g., "6,8,10") |
| `force_update_title` | String | `Update Required` | Dialog title |
| `force_update_message` | String | `A critical security update is available...` | Dialog message |
| `soft_update_enabled` | Boolean | `false` | Enable soft update prompts |
| `soft_update_min_version_code` | Number | `1` | Minimum for soft prompt |
| `soft_update_blocked_versions` | String | `""` | Comma-separated versions for soft prompt |

### Step 3: Publish Changes

Click **"Publish changes"** to make them live.

---

## 📱 Usage Examples

### Example 1: Block All Versions Below 3

```
force_update_enabled = true
force_update_min_version_code = 3
force_update_blocked_versions = ""
```

**Result:** Versions 1, 2 will be blocked

---

### Example 2: Block Specific Vulnerable Version

```
force_update_enabled = true
force_update_min_version_code = 1
force_update_blocked_versions = "6"
```

**Result:** Only version 6 will be blocked

---

### Example 3: Block Old + Specific Versions

```
force_update_enabled = true
force_update_min_version_code = 3
force_update_blocked_versions = "6,8,10"
```

**Result:** 
- ❌ Versions 1, 2 (below minimum)
- ✅ Versions 3, 4, 5, 7, 9 (OK)
- ❌ Versions 6, 8, 10 (in blocked list)

---

### Example 4: Soft Prompt + Force Block

```
# Force update (blocking)
force_update_enabled = true
force_update_min_version_code = 1
force_update_blocked_versions = "6"

# Soft update (dismissible)
soft_update_enabled = true
soft_update_min_version_code = 5
soft_update_blocked_versions = ""
```

**Result:**
- ❌ **Version 6**: Force blocked (critical vulnerability)
- 🟡 **Versions 1-4**: Soft prompt (can dismiss and continue)
- ✅ **Versions 5, 7+**: No prompt

---

## 🚀 How It Works

### App Launch Flow

```
1. App starts
   ↓
2. RemoteConfigManager fetches config
   ↓
3. ForceUpdateChecker checks version
   ↓
4a. FORCE_UPDATE → Show blocking dialog → Play Store
4b. SOFT_UPDATE → Show dismissible prompt → Continue or Play Store
4c. UP_TO_DATE → Show normal app
```

### Version Check Logic

```kotlin
if (currentVersion < minVersion) → FORCE UPDATE
if (currentVersion in blockedVersions) → FORCE UPDATE
if (currentVersion < softMinVersion) → SOFT UPDATE
if (currentVersion in softBlockedVersions) → SOFT UPDATE
else → UP TO DATE
```

---

## 🎯 Real-World Scenario

**Situation:** Version 6 has a critical security vulnerability

**Action:**
1. Go to Firebase Console → Remote Config
2. Set `force_update_enabled` = `true`
3. Set `force_update_blocked_versions` = `"6"`
4. Set `force_update_message` = `"This version has a critical security issue. Please update immediately."`
5. Click **Publish changes**

**Result:** Within 1 hour (or instantly if they restart the app):
- Users on version 6 will see a blocking dialog
- They cannot use the app until they update
- Users on other versions are unaffected

---

## 🔄 To Disable Force Update

Simply set `force_update_enabled` = `false` and publish.

---

## 📝 Current App Version

Your current version: **1.0.4** (code: **4**)

Next version will be: **1.0.5** (code: **5**)

Use `./bump-version.sh` to increment version.

---

## ⚠️ Important Notes

1. **Changes Take Effect:** Within 1 hour by default (faster if user restarts app)
2. **Cache:** Remote Config uses caching for performance
3. **Testing:** Use `force_update_enabled = false` during testing
4. **Play Store:** Dialog automatically opens Play Store for updates
5. **Version Codes:** Must match what's in [build.gradle.kts](build.gradle.kts#L18)

---

## 🧪 Testing

### Test Force Update:

1. Set in Firebase Console:
   ```
   force_update_enabled = true
   force_update_min_version_code = 999
   ```
2. Restart app
3. You should see blocking dialog

### Test Soft Update:

1. Set in Firebase Console:
   ```
   soft_update_enabled = true
   soft_update_min_version_code = 999
   ```
2. Restart app
3. You should see dismissible prompt

---

## 📂 Files Modified/Created

| File | Action | Lines |
|------|--------|-------|
| `RemoteConfigManager.kt` | Modified | +82 lines |
| `ForceUpdateChecker.kt` | Created | 76 lines |
| `ForceUpdateDialog.kt` | Created | 217 lines |
| `MainActivity.kt` | Modified | +67 lines |
| `build.gradle.kts` | Modified | +1 line |

**Total:** ~443 lines of code added

---

## ✨ Features

✅ **Flexible Control**
   - Minimum version blocking
   - Specific version blocking
   - Both can be used together

✅ **Two Update Modes**
   - Force update (blocking)
   - Soft update (dismissible)

✅ **No Code Deployment**
   - Change settings instantly via Firebase Console
   - No need to rebuild or redeploy

✅ **Beautiful UI**
   - Matches app's indigo theme
   - Clear messaging
   - Professional design

✅ **Robust Logic**
   - Handles offline scenarios
   - Falls back to cached values
   - Extensive logging for debugging

---

## 🎉 Ready to Use!

The force update system is now live and ready to protect your users from vulnerable app versions.
