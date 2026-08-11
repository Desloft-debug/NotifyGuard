# Silent Notifications

Hides advertising notifications and mute calls from unknown numbers. Verification codes, account transactions, battery status messages, and emergency alerts remain visible.

For Android 10 and later. The app is not available on Google Play—it is built from source and installed as a file.

---

## Installation

**Obtainium** is the most convenient method: the app automatically monitors releases and updates.
Install [Obtainium](https://github.com/ImranR98/Obtainium), click "Add App," and paste the link to this repository.

**APK file.** [Releases](../../releases) → latest release → `NotifyGuard-vX.X.apk`. Open the file on your phone and allow installation from this source.

**F-Droid.** The app is submitted to the IzzyOnDroid repository. Once enabled, it will be available in any F-Droid client after adding the IzzyOnDroid repository.

## First Setup

### Reading Notifications — Required

Click "Open Settings," find "Silent Notifications" in the list, and turn on the toggle.

**If the toggle is grayed out and the system reports a security restriction**, this is a security feature for Android 13+ apps installed outside the store. To turn it off, go to Settings → Apps → Silent Notifications → three dots in the upper right corner → **Allow Restricted Settings**. Then, go back and turn on the toggle.

### Access to Contacts — if you need silent calls

This is necessary to distinguish known numbers from unknown ones. Without it, the app considers everyone known and does not mute anyone.

### Call screening app - if you need silent calls

Tap "Assign" and confirm. Note: this role is limited to one on your phone. While "Silent Notifications" is assigned to it, built-in caller ID or Truecaller won't work.

---

## How to use it

### Hide ads

Main switch. The app reads the text of each notification and removes those that look like ads.

**Never hidden:** emergency notifications, device status (battery, memory, overheating), calls, alarms, verification codes, and account transactions.

### Strict mode

Hides everything except the whitelist of apps, codes, and money transactions. Suitable if you have too many notifications and it's easier to allow one at a time than to block them.

### What was hidden

Main screen for settings. Here's a list of removed notifications with the reason—you can see which word triggered them: "Advertising word: 'discount'."

If the filter was incorrect, there's a "Don't hide this app anymore" button under the entry—it'll add it to your whitelist with one click.

It's worth checking here regularly for the first day or two. The dictionaries are universally selected; you'll almost certainly need to adjust them for your banks and stores.

### Stop Words

Your own words that hide the notification. These are more stringent than built-in rules: they trigger even if the text contains an amount or the word "code."

For example, if a bank sends loan offers, add "loan." Notifications like "Loan approved for 500,000 ₽" will disappear, but "Transfer of 3,000 ₽ from Ivan" will remain, because stop words are searched separately from monetary transactions.

The word is searched **at the beginning**: `skidk` will match "discounts" and "discount", `credit` will match "loans" and "credit". It won't work within another word: `code` won't match "promocode".

Emergency alerts and charging messages can't be suppressed with stop words—this check occurs first.

### Exception Words

The downside: these words never hide notifications. This is where you should add the names of banks, delivery companies, and services that are important to you.

### Select Apps

Whitelist: Notifications from selected apps are not filtered at all.

It's best to add your banking app here immediately, and disable its promotional emails within the bank itself—this way, no transfers will be missed.

### Mute Unknown Numbers

Calls from numbers not in your contacts are silent and don't vibrate. The call isn't dropped: it remains in the call log, appears as missed, and the caller can try again. Emergency services don't go through this check at all.

---

## What you need to understand

**Ads may flash.** Android grants apps access to notifications after they've been displayed. The banner appears for a split second and then disappears, and the sound may play. Removing this without system permissions is impossible—it's a system limitation, not a bug.

**The word filter is incorrect.** It doesn't understand the meaning, only looks for matches. Sometimes something useful will be hidden, while something advertising will get through. The log and stop words exist precisely to correct this as it happens.

**Nothing is sent anywhere.** Notification texts are parsed on the phone; the app doesn't need the internet; the log is stored locally. Permission to read SMS messages is not requested—the app only sees the notification text.

**Don't rely on this in critical situations.** If you're expecting an important call from an unknown number, turn off the mute feature in advance.

---

## Update

Download the new APK from the releases and install it over it. Settings, st
