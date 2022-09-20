package eu.kanade.tachiyomi.ui.animelib

import android.content.Context
import android.util.AttributeSet
import android.view.View
import com.bluelinelabs.conductor.Router
import eu.kanade.domain.category.interactor.UpdateCategoryAnime
import eu.kanade.domain.category.model.CategoryUpdate
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.data.database.models.Category
import eu.kanade.tachiyomi.data.database.models.toDomainCategory
import eu.kanade.tachiyomi.data.preference.PreferencesHelper
import eu.kanade.tachiyomi.data.track.MangaTrackService
import eu.kanade.tachiyomi.data.track.TrackManager
import eu.kanade.tachiyomi.data.track.TrackService
import eu.kanade.tachiyomi.ui.library.setting.DisplayModeSetting
import eu.kanade.tachiyomi.ui.library.setting.SortDirectionSetting
import eu.kanade.tachiyomi.ui.library.setting.SortModeSetting
import eu.kanade.tachiyomi.util.lang.launchIO
import eu.kanade.tachiyomi.widget.ExtendedNavigationView
import eu.kanade.tachiyomi.widget.ExtendedNavigationView.Item.TriStateGroup.State
import eu.kanade.tachiyomi.widget.sheet.TabbedBottomSheetDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy

class AnimelibSettingsSheet(
    router: Router,
    private val trackManager: TrackManager = Injekt.get(),
    private val updateCategory: UpdateCategoryAnime = Injekt.get(),
    onGroupClickListener: (ExtendedNavigationView.Group) -> Unit,
) : TabbedBottomSheetDialog(router.activity!!) {

    val filters: Filter
    private val sort: Sort
    private val display: Display

    val sheetScope = CoroutineScope(Job() + Dispatchers.IO)

    init {
        filters = Filter(router.activity!!)
        filters.onGroupClicked = onGroupClickListener

        sort = Sort(router.activity!!)
        sort.onGroupClicked = onGroupClickListener

        display = Display(router.activity!!)
        display.onGroupClicked = onGroupClickListener
    }

    /**
     * adjusts selected button to match real state.
     * @param currentCategory ID of currently shown category
     */
    fun show(currentCategory: Category) {
        sort.currentCategory = currentCategory
        sort.adjustDisplaySelection()
        display.currentCategory = currentCategory
        display.adjustDisplaySelection()
        super.show()
    }

    override fun getTabViews(): List<View> = listOf(
        filters,
        sort,
        display,
    )

    override fun getTabTitles(): List<Int> = listOf(
        R.string.action_filter,
        R.string.action_sort,
        R.string.action_display,
    )

    /**
     * Filters group (unread, downloaded, ...).
     */
    inner class Filter @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Settings(context, attrs) {

        private val filterGroup = FilterGroup()

        init {
            setGroups(listOf(filterGroup))
        }

        /**
         * Returns true if there's at least one filter from [FilterGroup] active.
         */
        fun hasActiveFilters(): Boolean {
            return filterGroup.items.filterIsInstance<Item.TriStateGroup>().any { it.state != State.IGNORE.value }
        }

        inner class FilterGroup : Group {

            private val downloaded = Item.TriStateGroup(R.string.action_filter_downloaded, this)
            private val unseen = Item.TriStateGroup(R.string.action_filter_unseen, this)
            private val started = Item.TriStateGroup(R.string.action_filter_started, this)
            private val completed = Item.TriStateGroup(R.string.completed, this)
            private val trackFilters: Map<Long, Item.TriStateGroup>

            override val header = null
            override val items: List<Item>
            override val footer = null

            init {
                trackManager.services.filter { service ->
                    service.isLogged && service !is MangaTrackService
                }.also { services ->
                    val size = services.size
                    trackFilters = services.associate { service ->
                        Pair(service.id, Item.TriStateGroup(getServiceResId(service, size), this))
                    }
                    val list: MutableList<Item> = mutableListOf(downloaded, unseen, started, completed)
                    if (size > 1) list.add(Item.Header(R.string.action_filter_tracked))
                    list.addAll(trackFilters.values)
                    items = list
                }
            }

            private fun getServiceResId(service: TrackService, size: Int): Int {
                return if (size > 1) service.nameRes() else R.string.action_filter_tracked
            }

            override fun initModels() {
                if (preferences.downloadedOnly().get()) {
                    downloaded.state = State.INCLUDE.value
                    downloaded.enabled = false
                } else {
                    downloaded.state = preferences.filterDownloaded().get()
                }
                unseen.state = preferences.filterUnread().get()
                started.state = preferences.filterStarted().get()
                completed.state = preferences.filterCompleted().get()

                trackFilters.forEach { trackFilter ->
                    trackFilter.value.state = preferences.filterTracking(trackFilter.key.toInt()).get()
                }
            }

            override fun onItemClicked(item: Item) {
                item as Item.TriStateGroup
                val newState = when (item.state) {
                    State.IGNORE.value -> State.INCLUDE.value
                    State.INCLUDE.value -> State.EXCLUDE.value
                    State.EXCLUDE.value -> State.IGNORE.value
                    else -> throw Exception("Unknown State")
                }
                item.state = newState
                when (item) {
                    downloaded -> preferences.filterDownloaded().set(newState)
                    unseen -> preferences.filterUnread().set(newState)
                    started -> preferences.filterStarted().set(newState)
                    completed -> preferences.filterCompleted().set(newState)
                    else -> {
                        trackFilters.forEach { trackFilter ->
                            if (trackFilter.value == item) {
                                preferences.filterTracking(trackFilter.key.toInt()).set(newState)
                            }
                        }
                    }
                }

                adapter.notifyItemChanged(item)
            }
        }
    }

    /**
     * Sorting group (alphabetically, by last read, ...) and ascending or descending.
     */
    inner class Sort @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        Settings(context, attrs) {

        private val sort = SortGroup()

        init {
            setGroups(listOf(sort))
        }

        // Refreshes Display Setting selections
        fun adjustDisplaySelection() {
            sort.initModels()
            sort.items.forEach { adapter.notifyItemChanged(it) }
        }

        inner class SortGroup : Group {

            private val alphabetically = Item.MultiSort(R.string.action_sort_alpha, this)
            private val total = Item.MultiSort(R.string.action_sort_total_episodes, this)
            private val lastSeen = Item.MultiSort(R.string.action_sort_last_seen, this)
            private val lastChecked = Item.MultiSort(R.string.action_sort_last_anime_update, this)
            private val unseen = Item.MultiSort(R.string.action_sort_unseen_count, this)
            private val latestEpisode = Item.MultiSort(R.string.action_sort_latest_episode, this)
            private val episodeFetchDate = Item.MultiSort(R.string.action_sort_chapter_fetch_date, this)
            private val dateAdded = Item.MultiSort(R.string.action_sort_date_added, this)

            override val header = null
            override val items =
                listOf(alphabetically, lastSeen, lastChecked, unseen, total, latestEpisode, episodeFetchDate, dateAdded)
            override val footer = null

            override fun initModels() {
                val sorting = SortModeSetting.get(preferences, currentCategory?.toDomainCategory())
                val order = if (SortDirectionSetting.get(preferences, currentCategory?.toDomainCategory()) == SortDirectionSetting.ASCENDING) {
                    Item.MultiSort.SORT_ASC
                } else {
                    Item.MultiSort.SORT_DESC
                }

                alphabetically.state =
                    if (sorting == SortModeSetting.ALPHABETICAL) order else Item.MultiSort.SORT_NONE
                lastSeen.state =
                    if (sorting == SortModeSetting.LAST_READ) order else Item.MultiSort.SORT_NONE
                lastChecked.state =
                    if (sorting == SortModeSetting.LAST_MANGA_UPDATE) order else Item.MultiSort.SORT_NONE
                unseen.state =
                    if (sorting == SortModeSetting.UNREAD_COUNT) order else Item.MultiSort.SORT_NONE
                total.state =
                    if (sorting == SortModeSetting.TOTAL_CHAPTERS) order else Item.MultiSort.SORT_NONE
                latestEpisode.state =
                    if (sorting == SortModeSetting.LATEST_CHAPTER) order else Item.MultiSort.SORT_NONE
                episodeFetchDate.state =
                    if (sorting == SortModeSetting.CHAPTER_FETCH_DATE) order else Item.MultiSort.SORT_NONE
                dateAdded.state =
                    if (sorting == SortModeSetting.DATE_ADDED) order else Item.MultiSort.SORT_NONE
            }

            override fun onItemClicked(item: Item) {
                item as Item.MultiStateGroup
                val prevState = item.state

                item.group.items.forEach {
                    (it as Item.MultiStateGroup).state =
                        Item.MultiSort.SORT_NONE
                }
                item.state = when (prevState) {
                    Item.MultiSort.SORT_NONE -> Item.MultiSort.SORT_ASC
                    Item.MultiSort.SORT_ASC -> Item.MultiSort.SORT_DESC
                    Item.MultiSort.SORT_DESC -> Item.MultiSort.SORT_ASC
                    else -> throw Exception("Unknown state")
                }

                setSortModePreference(item)

                setSortDirectionPreference(item)

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }

            private fun setSortDirectionPreference(item: Item.MultiStateGroup) {
                val flag = if (item.state == Item.MultiSort.SORT_ASC) {
                    SortDirectionSetting.ASCENDING
                } else {
                    SortDirectionSetting.DESCENDING
                }

                if (preferences.categorizedDisplaySettings().get() && currentCategory != null && currentCategory?.id != 0) {
                    currentCategory?.sortDirection = flag.flag.toInt()

                    sheetScope.launchIO {
                        updateCategory.await(
                            CategoryUpdate(
                                id = currentCategory!!.id?.toLong()!!,
                                flags = currentCategory!!.flags.toLong(),
                            ),
                        )
                    }
                } else {
                    preferences.librarySortingAscending().set(flag)
                }
            }

            private fun setSortModePreference(item: Item) {
                val flag = when (item) {
                    alphabetically -> SortModeSetting.ALPHABETICAL
                    lastSeen -> SortModeSetting.LAST_READ
                    lastChecked -> SortModeSetting.LAST_MANGA_UPDATE
                    unseen -> SortModeSetting.UNREAD_COUNT
                    total -> SortModeSetting.TOTAL_CHAPTERS
                    latestEpisode -> SortModeSetting.LATEST_CHAPTER
                    episodeFetchDate -> SortModeSetting.CHAPTER_FETCH_DATE
                    dateAdded -> SortModeSetting.DATE_ADDED
                    else -> throw NotImplementedError("Unknown display mode")
                }

                if (preferences.categorizedDisplaySettings().get() && currentCategory != null && currentCategory?.id != 0) {
                    currentCategory?.sortMode = flag.flag.toInt()

                    sheetScope.launchIO {
                        updateCategory.await(
                            CategoryUpdate(
                                id = currentCategory!!.id?.toLong()!!,
                                flags = currentCategory!!.flags.toLong(),
                            ),
                        )
                    }
                } else {
                    preferences.librarySortingMode().set(flag)
                }
            }
        }
    }

    /**
     * Display group, to show the animelib as a list or a grid.
     */
    inner class Display @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
        AnimelibSettingsSheet.Settings(context, attrs) {

        private val displayGroup: AnimelibSettingsSheet.Display.DisplayGroup
        private val badgeGroup: AnimelibSettingsSheet.Display.BadgeGroup
        private val tabsGroup: AnimelibSettingsSheet.Display.TabsGroup

        init {
            displayGroup = DisplayGroup()
            badgeGroup = BadgeGroup()
            tabsGroup = TabsGroup()
            setGroups(listOf(displayGroup, badgeGroup, tabsGroup))
        }

        // Refreshes Display Setting selections
        fun adjustDisplaySelection() {
            val mode = getDisplayModePreference()
            displayGroup.setGroupSelections(mode)
            displayGroup.items.forEach { adapter.notifyItemChanged(it) }
        }

        // Gets user preference of currently selected display mode at current category
        private fun getDisplayModePreference(): DisplayModeSetting {
            return if (preferences.categorizedDisplaySettings().get() && currentCategory != null && currentCategory?.id != 0) {
                DisplayModeSetting.fromFlag(currentCategory?.displayMode?.toLong())
            } else {
                preferences.libraryDisplayMode().get()
            }
        }

        inner class DisplayGroup : Group {

            private val compactGrid = Item.Radio(R.string.action_display_grid, this)
            private val comfortableGrid = Item.Radio(R.string.action_display_comfortable_grid, this)
            private val coverOnlyGrid = Item.Radio(R.string.action_display_cover_only_grid, this)
            private val list = Item.Radio(R.string.action_display_list, this)

            override val header = Item.Header(R.string.action_display_mode)
            override val items = listOf(compactGrid, comfortableGrid, coverOnlyGrid, list)
            override val footer = null

            override fun initModels() {
                val mode = getDisplayModePreference()
                setGroupSelections(mode)
            }

            override fun onItemClicked(item: Item) {
                item as Item.Radio
                if (item.checked) return

                item.group.items.forEach { (it as Item.Radio).checked = false }
                item.checked = true

                setDisplayModePreference(item)

                item.group.items.forEach { adapter.notifyItemChanged(it) }
            }

            // Sets display group selections based on given mode
            fun setGroupSelections(mode: DisplayModeSetting) {
                compactGrid.checked = mode == DisplayModeSetting.COMPACT_GRID
                comfortableGrid.checked = mode == DisplayModeSetting.COMFORTABLE_GRID
                coverOnlyGrid.checked = mode == DisplayModeSetting.COVER_ONLY_GRID
                list.checked = mode == DisplayModeSetting.LIST
            }

            private fun setDisplayModePreference(item: Item) {
                val flag = when (item) {
                    compactGrid -> DisplayModeSetting.COMPACT_GRID
                    comfortableGrid -> DisplayModeSetting.COMFORTABLE_GRID
                    coverOnlyGrid -> DisplayModeSetting.COVER_ONLY_GRID
                    list -> DisplayModeSetting.LIST
                    else -> throw NotImplementedError("Unknown display mode")
                }

                if (preferences.categorizedDisplaySettings().get() && currentCategory != null && currentCategory?.id != 0) {
                    currentCategory?.displayMode = flag.flag.toInt()

                    sheetScope.launchIO {
                        updateCategory.await(
                            CategoryUpdate(
                                id = currentCategory!!.id?.toLong()!!,
                                flags = currentCategory!!.flags.toLong(),
                            ),
                        )
                    }
                } else {
                    preferences.libraryDisplayMode().set(flag)
                }
            }
        }

        inner class BadgeGroup : Group {
            private val downloadBadge = Item.CheckboxGroup(R.string.action_display_download_badge_anime, this)
            private val unseenBadge = Item.CheckboxGroup(R.string.action_display_unseen_badge, this)
            private val localBadge = Item.CheckboxGroup(R.string.action_display_local_badge_anime, this)
            private val languageBadge = Item.CheckboxGroup(R.string.action_display_language_badge, this)

            override val header = Item.Header(R.string.badges_header)
            override val items = listOf(downloadBadge, unseenBadge, localBadge, languageBadge)
            override val footer = null

            override fun initModels() {
                downloadBadge.checked = preferences.downloadBadge().get()
                unseenBadge.checked = preferences.unreadBadge().get()
                localBadge.checked = preferences.localBadge().get()
                languageBadge.checked = preferences.languageBadge().get()
            }

            override fun onItemClicked(item: Item) {
                item as Item.CheckboxGroup
                item.checked = !item.checked
                when (item) {
                    downloadBadge -> preferences.downloadBadge().set(item.checked)
                    unseenBadge -> preferences.unreadBadge().set(item.checked)
                    localBadge -> preferences.localBadge().set(item.checked)
                    languageBadge -> preferences.languageBadge().set(item.checked)
                    else -> {}
                }
                adapter.notifyItemChanged(item)
            }
        }

        inner class TabsGroup : Group {
            private val showTabs = Item.CheckboxGroup(R.string.action_display_show_tabs, this)
            private val showNumberOfItems = Item.CheckboxGroup(R.string.action_display_show_number_of_items, this)

            override val header = Item.Header(R.string.tabs_header)
            override val items = listOf(showTabs, showNumberOfItems)
            override val footer = null

            override fun initModels() {
                showTabs.checked = preferences.animeCategoryTabs().get()
                showNumberOfItems.checked = preferences.animeCategoryNumberOfItems().get()
            }

            override fun onItemClicked(item: Item) {
                item as Item.CheckboxGroup
                item.checked = !item.checked
                when (item) {
                    showTabs -> preferences.animeCategoryTabs().set(item.checked)
                    showNumberOfItems -> preferences.animeCategoryNumberOfItems().set(item.checked)
                    else -> {}
                }
                adapter.notifyItemChanged(item)
            }
        }
    }

    open inner class Settings(context: Context, attrs: AttributeSet?) :
        ExtendedNavigationView(context, attrs) {

        val preferences: PreferencesHelper by injectLazy()
        lateinit var adapter: Adapter

        /**
         * Click listener to notify the parent fragment when an item from a group is clicked.
         */
        var onGroupClicked: (Group) -> Unit = {}

        var currentCategory: Category? = null

        fun setGroups(groups: List<Group>) {
            adapter = Adapter(groups.map { it.createItems() }.flatten())
            recycler.adapter = adapter

            groups.forEach { it.initModels() }
            addView(recycler)
        }

        /**
         * Adapter of the recycler view.
         */
        inner class Adapter(items: List<Item>) : ExtendedNavigationView.Adapter(items) {

            override fun onItemClicked(item: Item) {
                if (item is GroupedItem) {
                    item.group.onItemClicked(item)
                    onGroupClicked(item.group)
                }
            }
        }
    }
}