# Anilili-TV

Anilili is a native Android anime streaming client built with Kotlin, Jetpack Compose, and Media3. **This specific fork has been optimized for Android TV devices**, with modern Material 3 interface and full D-pad remote support. 

Metadata, login, library lists, and progress sync are powered by AniList, while episodes and stream sources are resolved from multiple providers: Miruro, AniKoto, ReAnime, AniZone, AnimeGG, AniNeko, and 2DHive.

Miruro streams are requested through the Miruro pipe endpoint and decoded on device. Additional provider sources are resolved through the Anivexa-backed provider client. HLS streams play with ExoPlayer; embed providers and fallback playback use WebView.

> Personal and educational project. This app is not affiliated with AniList, Miruro, AniKoto, ReAnime, AniZone, AnimeGG, AniNeko, or 2DHive. Distribute as a sideloaded APK.

## Screenshots

| Home | Search | Details |
| :---: | :---: | :---: |
| <a href="showcase/home.png"><img src="showcase/home.png" width="450" alt="Anilili TV home screen" /></a> | <a href="showcase/search.png"><img src="showcase/search.png" width="450" alt="Anime search and browse" /></a> | <a href="showcase/details.png"><img src="showcase/details.png" width="450" alt="Anime details and episode selection" /></a> |

| Player | Player controls | Library |
| :---: | :---: | :---: |
| <a href="showcase/player.png"><img src="showcase/player.png" width="450" alt="Player" /></a> | <a href="showcase/player2.png"><img src="showcase/player2.png" width="450" alt="Schedule" /></a> | <a href="showcase/schedule.png"><img src="showcase/library.png" width="450" alt="Library with watchlist and continue watching" /></a> |
