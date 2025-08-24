# A-larm

A-larmはAIを活用した、あなたの理想の生活習慣をサポートする目覚ましアプリです。

## 特徴

- **AIとの対話:** 設定した起床時間になると、AIがあなたに話しかけて起床を促します。
- **キャラクター設定:** AIのキャラクターを自由に設定できます。
- **フォールバック機能:** LLMの応答がない場合やTTSに失敗した場合は、通常のアラームとして機能します。

## アーキテクチャ

このプロジェクトは、責務の分離を目的とした**クリーンアーキテクチャ**を採用しています。

- **モジュール:** `app` (UI), `domain` (ビジネスロジック), `infra` (データ)
- **UIパターン:** MVVM (Model-View-ViewModel)
- **主要ライブラリ:**
    - Jetpack Compose
    - Hilt
    - Kotlin Coroutines

いいね — AndroidでGoogleカレンダーの予定を取ってくる方法は主に**2通り**
あります。目的に合わせて選べます（どっちが良いか分からなければ用途を書いてくれれば具体コード出します）。

# 1) 端末に入っているカレンダーを直接読む（`CalendarProvider` / `CalendarContract`）

* ユーザーの端末に同期されているカレンダー（Googleアカウントで同期されたものも含む）へローカルにアクセスします。アプリ単体で実装でき、Google
  Cloud側の設定やOAuthは不要。ただしユーザーの許可が必要（`READ_CALENDAR` / `WRITE_CALENDAR`）。
*
用途：端末上の予定を見せたい／編集したい（ユーザー端末で同期済みならOK）。公式ドキュメント参照。([Android Developers][1])

簡単な読み取りサンプル（Kotlin）：

```kotlin
suspend fun queryEvents(
    context: Context,
    startMillis: Long,
    endMillis: Long
): List<EventItem> = withContext(Dispatchers.IO) {
    val uri = CalendarContract.Events.CONTENT_URI
    val projection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND,
        CalendarContract.Events.CALENDAR_DISPLAY_NAME
    )
    val selection =
        "(${CalendarContract.Events.DTSTART} >= ?) AND (${CalendarContract.Events.DTEND} <= ?)"
    val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
    val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, null)
    val out = mutableListOf<EventItem>()
    cursor?.use {
        val idIdx = it.getColumnIndexOrThrow(CalendarContract.Events._ID)
        val titleIdx = it.getColumnIndexOrThrow(CalendarContract.Events.TITLE)
        val startIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTSTART)
        val endIdx = it.getColumnIndexOrThrow(CalendarContract.Events.DTEND)
        val calNameIdx = it.getColumnIndexOrThrow(CalendarContract.Events.CALENDAR_DISPLAY_NAME)
        while (it.moveToNext()) {
            out += EventItem(
                id = it.getLong(idIdx),
                title = it.getString(titleIdx) ?: "",
                start = it.getLong(startIdx),
                end = it.getLong(endIdx),
                calendarName = it.getString(calNameIdx) ?: ""
            )
        }
    }
    out
}
data class EventItem(
    val id: Long,
    val title: String,
    val start: Long,
    val end: Long,
    val calendarName: String
)
```

注意点：

* `READ_CALENDAR`のランタイム許可を取得すること（Android 6+）。([Android Developers][1])

---

# 2) Google Calendar（クラウド）API を直接呼ぶ（Google Calendar REST API）

* アプリからGoogleのサーバ上のカレンダー（ユーザーの全デバイスにまたがる予定）を直接読み書きしたい場合は、Google
  Calendar API（REST）を使います。OAuth
  2.0でユーザーの許可を得る必要があります。([Google for Developers][2])
* 利点：サーバ同期/複数端末間の一貫性、詳細なフィルタやACL操作などAPIでできることが多い。欠点：Google
  Cloud側のプロジェクト設定・OAuth同意画面・スコープ管理が必要。

大まかな手順（要点）：

1. Google Cloud Consoleでプロジェクトを作成 → **Calendar API を有効化**。
2. OAuth 同意画面を設定（テストユーザーや公開設定、スコープの申請が必要な場合あり）。
3. Androidアプリのパッケージ名と SHA-1 をOAuthクライアントに登録（Android向けクライアントID）。
4. アプリで Google Sign-In / Google Identity を使ってユーザー認証・権限取得（
   `https://www.googleapis.com/auth/calendar.readonly`
   等のスコープ）。([Android Developers][3], [Google for Developers][4])
5. 取得したアクセストークン（またはサーバで交換して得たトークン）を `Authorization: Bearer <token>` で
   Calendar REST API に渡してイベント一覧取得等を行う。([Google for Developers][2])

簡易フロー（Kotlin + OkHttp の例、トークン取得部分は下に補足）：

```kotlin
// 1) アクセストークンを手に入れたら（下で取得方法を説明）：
suspend fun fetchPrimaryCalendarEvents(accessToken: String): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val req = Request.Builder()
        .url("https://www.googleapis.com/calendar/v3/calendars/primary/events")
        .addHeader("Authorization", "Bearer $accessToken")
        .build()
    client.newCall(req).execute().use { it.body?.string() ?: "" }
}
```

アクセストークン取得（候補）：

* **Google Sign-In を使ってスコープをリクエスト**し、`GoogleSignInAccount` を得る。端末内でトークンが必要なら
  `GoogleAuthUtil.getToken(context, account.account, "oauth2:<scope>")` を使って短期トークンを取得する（ただし
  Google Play services の許可と注意点あり）。長期的に継続してアクセスするなら「サーバでの認可コード交換（offline
  access / refresh token）」を使うのが安全。([Android Developers][3], [Google for Developers][4])

重要な注意点：

*
Android向けのOAuth設定（パッケージ名＋SHA-1）の登録忘れで認証失敗することが多い。([Google for Developers][4])
* 権限（スコープ）は最小限に。読み取りだけなら `calendar.readonly` を使う。([Google for Developers][2])

---

# どちらを選ぶべきか（簡潔判断）

* 「ユーザーの**端末に同期されたカレンダー**を読むだけ」 → **CalendarProvider（CalendarContract）**
  が手っ取り早く簡単。権限だけでOK。([Android Developers][1])
*
「クラウド上のGoogleカレンダー（複数端末・共有カレンダー等）を確実に操作したい／サーバから予定を扱いたい」 →
**Google Calendar API + OAuth2（Google Sign-In）** を使う。([Google for Developers][2])

---

必要なら次でやること（私が作れるもの）：

* あなたの用途に合わせた**具体的なサンプルプロジェクト**（A: CalendarContract の実装、または B: Google
  Sign-In + REST 呼び出し＋トークン取得のフルワークフロー）を用意します。
* 「端末ローカルで良い」か「クラウドで確実に扱いたい」かだけ教えてくれれば、必要な AndroidManifest /
  permission / Gradle 依存（play-services-auth 等）含めてコード一式出します。

どっちで進める？ (手早く試したいなら「ローカル」でサンプルを即出します)

[1]: https://developer.android.com/identity/providers/calendar-provider?utm_source=chatgpt.com "Calendar provider overview | Identity"

[2]: https://developers.google.com/workspace/calendar/api/guides/overview?utm_source=chatgpt.com "Google Calendar API overview"

[3]: https://developer.android.com/identity/sign-in/credential-manager-siwg?utm_source=chatgpt.com "Authenticate users with Sign in with Google | Identity"

[4]: https://developers.google.com/identity/protocols/oauth2?utm_source=chatgpt.com "Using OAuth 2.0 to Access Google APIs | Authorization"
