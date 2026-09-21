# Plan: Convert Manga to Kotlin & Create Chapter Entity

We will execute the following steps to implement the first task:

1. **Configure Gradle JPA Plugin:** Add the `kotlin-jpa` plugin to [build.gradle.kts](file:///C:/Users/anwar/Documents/Work-Academia/LearningCode/Projects/the-archive/build.gradle.kts) to automatically generate no-argument constructors for Kotlin JPA entities.
2. **Convert Manga Entity to Kotlin:** Delete the Java version [Manga.java](file:///C:/Users/anwar/Documents/Work-Academia/LearningCode/Projects/the-archive/src/main/java/com/archive/domain/manga/Manga.java) and write a Kotlin equivalent `Manga.kt` under `src/main/kotlin/com/archive/domain/manga/Manga.kt`.
3. **Create Chapter Entity:** Add `Chapter.kt` under `src/main/kotlin/com/archive/domain/manga/Chapter.kt` to represent individual chapters of a Manga, including `@ManyToOne` mapping and `OffsetDateTime` timestamps.
4. **Create Chapter Repository:** Add `ChapterRepository.kt` under `src/main/kotlin/com/archive/domain/manga/ChapterRepository.kt` to expose Spring Data JPA operations for the `Chapter` entity.

---

## 1. Gradle Changes

### [build.gradle.kts](file:///C:/Users/anwar/Documents/Work-Academia/LearningCode/Projects/the-archive/build.gradle.kts)

We will add `kotlin("plugin.jpa")` to the `plugins` block:

```diff
 plugins {
     java
     id("org.springframework.boot") version "4.0.5"
     id("io.spring.dependency-management") version "1.1.5"
     kotlin("jvm") version "2.1.0"
+    kotlin("plugin.jpa") version "2.1.0"
 }
```

---

## 2. File Removals

* **Delete:** [Manga.java](file:///C:/Users/anwar/Documents/Work-Academia/LearningCode/Projects/the-archive/src/main/java/com/archive/domain/manga/Manga.java)

---

## 3. New Kotlin Entities

We will place our new Kotlin files under `src/main/kotlin/com/archive/domain/manga/` to conform to Kotlin JVM source conventions.

### `Manga.kt`
```kotlin
package com.archive.domain.manga

import com.archive.core.model.BaseMedia
import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "manga")
open class Manga : BaseMedia() {

    var author: String? = null
    var artist: String? = null

    @Enumerated(EnumType.STRING)
    var type: MangaType? = null

    @Enumerated(EnumType.STRING)
    var status: MangaStatus? = null

    @ElementCollection
    @CollectionTable(
        name = "manga_genres",
        joinColumns = [JoinColumn(name = "manga_uuid")]
    )
    @Column(name = "genre")
    var genres: MutableSet<String> = mutableSetOf()

    // Harvesting/scraping state
    var storagePath: String? = null
    var totalChapters: Int = 0
    var downloadedChapters: Int = 0
    var lastChecked: OffsetDateTime? = null

    var isAdult: Boolean = false

    // CSS Selector Overrides (Area 4: User-Defined Scrapers)
    var customImageSelector: String? = null
    var customChapterSelector: String? = null

    @OneToMany(mappedBy = "manga", cascade = [CascadeType.ALL], orphanRemoval = true, fetch = FetchType.LAZY)
    var chapters: MutableList<Chapter> = mutableListOf()
}
```

### `Chapter.kt`
```kotlin
package com.archive.domain.manga

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.util.UUID

@Entity
@Table(name = "chapter")
open class Chapter {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "uuid", columnDefinition = "UUID", updatable = false, nullable = false)
    var id: UUID? = null

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manga_uuid", nullable = false)
    var manga: Manga? = null

    @Column(nullable = false)
    var chapterNumber: Double = 0.0

    var title: String? = null

    @Column(columnDefinition = "TEXT")
    var storagePath: String? = null

    var downloaded: Boolean = false

    @CreationTimestamp
    @Column(name = "date_added", updatable = false, columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var dateAdded: OffsetDateTime? = null

    @UpdateTimestamp
    @Column(name = "last_updated", columnDefinition = "TIMESTAMP WITH TIME ZONE")
    var lastUpdated: OffsetDateTime? = null
}
```

### `ChapterRepository.kt`
```kotlin
package com.archive.domain.manga

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface ChapterRepository : JpaRepository<Chapter, UUID>
```
