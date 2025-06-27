package com.example.music

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentResolver
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.media.MediaPlayer
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.graphics.toColorInt
import com.google.android.material.button.MaterialButton

data class MusicData(
    val id: Long,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumId: Long = -1L // gunakan default agar tidak error
)

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {

    private var isFiltering = false

    private val searchHandler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private lateinit var musicAdapter: MusicAdapter

    private lateinit var customScrollBarContainer: View

    private lateinit var sideBar: SideBar
    private lateinit var recyclerView: RecyclerView
    private lateinit var textLabel: TextView
    private val musicList = mutableListOf<MusicData>()
    private var isSearchOpen = false // ✅ Ini menyimpan status dengan benar

    //logika "@+id/scrollbar_include" dan "@+id/scrollbarContainer"
    private lateinit var scrollBarContainer: LinearLayout

    private var mediaPlayer: MediaPlayer? = null

    private fun playMusic(musicData: MusicData) {
        mediaPlayer?.release()
        val uri = ContentUris.withAppendedId(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            musicData.id
        )
        mediaPlayer = MediaPlayer().apply {
            setDataSource(this@MainActivity, uri)
            prepare()
            start()
        }
    }


    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    // Tentukan permission yang dibutuhkan sesuai versi Android
    private val requiredPermissions: Array<String>
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

    //logika "@+id/scrollbar_include" dan "@+id/scrollbarContainer"
    private fun scrollToLetter(letter: String) {
        val indexInList = musicList.indexOfFirst { it.title.startsWith(letter, ignoreCase = true) }
        if (indexInList != -1) {
            val layoutManager = recyclerView.layoutManager as? LinearLayoutManager
            val indexInRecyclerView = indexInList + 1 // +1 karena header
            layoutManager?.scrollToPositionWithOffset(indexInRecyclerView, 0)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        //logika "@+id/scrollbar_include" dan "@+id/scrollbarContainer"
        scrollBarContainer = findViewById<View>(R.id.scrollbar_include)
            .findViewById(R.id.scrollbarContainer)

        //logika "@+id/scrollbar_include" dan "@+id/scrollbarContainer"
        var lastTouchedLetter: String? = null
        val scrollBarInclude = findViewById<View>(R.id.scrollbar_include)
        val scrollbarContainer = scrollBarInclude.findViewById<LinearLayout>(R.id.scrollbarContainer)
        scrollbarContainer.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
                val location = IntArray(2)
                scrollbarContainer.getLocationOnScreen(location)
                val absoluteY = event.rawY.toInt()
                for (i in 0 until scrollbarContainer.childCount) {
                    val child = scrollbarContainer.getChildAt(i)
                    val childLocation = IntArray(2)
                    child.getLocationOnScreen(childLocation)
                    val top = childLocation[1]
                    val bottom = top + child.height
                    if (absoluteY in top..bottom && child is TextView) {
                        val text = child.text.toString()
                        if (text.length == 1 && text[0].isLetter() && text != lastTouchedLetter) {
                            lastTouchedLetter = text
                            scrollToLetter(text) // hanya dipanggil jika huruf berubah
                        }
                        break // berhenti di huruf pertama yang cocok
                    }
                }
                true
            } else {
                if (event.action == MotionEvent.ACTION_UP) {
                    lastTouchedLetter = null // reset saat jari diangkat
                }
                false
            }
        }



        //logika "@+id/customTabLayout"
        val buttonSongs = findViewById<AppCompatButton>(R.id.buttonSongs)
        val buttonFaporit = findViewById<AppCompatButton>(R.id.buttonFaporit)
        val buttonAlbum = findViewById<AppCompatButton>(R.id.buttonAlbum)
        val buttonFolder = findViewById<AppCompatButton>(R.id.buttonFolder)

        val buttons = listOf(buttonSongs, buttonFaporit, buttonAlbum, buttonFolder)

        buttons.forEach { button ->
            button.setOnClickListener {
                setActiveTab(button, buttons)
            }
        }
        setActiveTab(buttonSongs, buttons)




        sideBar = SideBar(this)
        sideBar.setupGestureAndSwipeOnly()

        // Sekarang kita bisa pakai view-nya dari SideBar:
        sideBar.buttonMenu.setOnClickListener {
            if (sideBar.sidebar.visibility == View.GONE) {
                sideBar.sidebar.visibility = View.VISIBLE
                sideBar.sidebar.animate().translationX(0f).setDuration(300).start()
                sideBar.sidebarOverlay.visibility = View.VISIBLE
            } else {
                sideBar.sidebar.animate().translationX(-sideBar.sidebar.width.toFloat()).setDuration(300).withEndAction {
                    sideBar.sidebar.visibility = View.GONE
                }.start()
                sideBar.sidebarOverlay.visibility = View.GONE
            }
        }


        val searchInput = findViewById<android.widget.EditText>(R.id.searchInput)

        // Pencarian langsung saat mengetik (real-time)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Hapus pencarian yang tertunda sebelumnya
                searchRunnable?.let { searchHandler.removeCallbacks(it) }

                // Jadwalkan pencarian baru setelah delay 500ms
                searchRunnable = Runnable {
                    val query = s.toString()
                    filterMusic(query)
                }

                searchHandler.postDelayed(searchRunnable!!, 300) // Delay 500ms
            }

            override fun afterTextChanged(s: Editable?) {}
        })


        // Menangani Enter/Keyboard Search
        searchInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchInput.text.toString()
                filterMusic(query) // Tetap jalankan filter tanpa toast


                // Sembunyikan keyboard
                val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                true
            } else {
                false
            }
        }

        val searchButton =
            findViewById<androidx.appcompat.widget.AppCompatImageButton>(R.id.button1)

        // Tombol pencarian ditekan
        searchButton.setOnClickListener {
            if (!isSearchOpen) {
                // Buka kolom pencarian
                searchInput.visibility = View.VISIBLE
                searchInput.alpha = 0f
                searchInput.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .withEndAction {
                        searchInput.requestFocus()

                        // Tampilkan keyboard
                        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(searchInput, InputMethodManager.SHOW_IMPLICIT)
                    }.start()
                searchButton.setImageResource(R.drawable.cencel_icon)
                isSearchOpen = true
            } else {
                if (searchInput.text.toString().isEmpty()) {
                    // Belum ngetik apa-apa = BATAL
                    searchInput.visibility = View.GONE
                    searchButton.setImageResource(R.drawable.search_icon)
                    isSearchOpen = false

                    // Sembunyikan keyboard
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                } else {
                    // Sudah ngetik = HAPUS teks pencarian, tampilkan semua lagu, dan tutup kolom pencarian
                    searchInput.setText("")
                    filterMusic("")

                    // Sembunyikan kolom pencarian
                    searchInput.visibility = View.GONE
                    searchButton.setImageResource(R.drawable.search_icon)
                    isSearchOpen = false

                    // Sembunyikan keyboard
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(searchInput.windowToken, 0)
                }

            }
        }



        customScrollBarContainer = findViewById(R.id.customScrollBarContainer)
        val customScrollBar = findViewById<View>(R.id.customScrollBar)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Untuk Android 11+ (API 30+)
            window.setDecorFitsSystemWindows(false)
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = false // false kalau mau ikon status bar putih
            controller.isAppearanceLightNavigationBars = false

            // Set warna transparan
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        } else {
            // Untuk Android 10 ke bawah
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    )
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
        }

        recyclerView = findViewById(R.id.recyclerViewMusic)
        textLabel = findViewById(R.id.songCount)
        recyclerView.layoutManager = LinearLayoutManager(this)

        musicAdapter = MusicAdapter(musicList, contentResolver) { musicData ->
            playMusic(musicData)
        }
        recyclerView.adapter = musicAdapter




        if (!hasPermissions()) {
            requestPermissions()
        } else {
            loadMusic()
            preloadAlbumArt()
        }
    }

    //logika "@+id/customTabLayout"
    fun setActiveTab(activeButton: AppCompatButton, allButtons: List<AppCompatButton>) {
        allButtons.forEach { button ->
            val isActive = button == activeButton
            button.isSelected = isActive

            if (isActive) {
                button.setTextColor("#FFFFFF".toColorInt())
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                button.setTypeface(null, Typeface.BOLD)
                // Panggil fungsi garis bawah mengikuti teks
                button.post { updateUnderlineInset(button) }
            } else {
                button.setTextColor("#80FFFFFF".toColorInt())
                button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                button.setTypeface(null, Typeface.NORMAL)
                button.setBackgroundResource(R.drawable.tab_background)
            }
        }
    }

    //logika "@+id/customTabLayout"
    fun updateUnderlineInset(button: AppCompatButton) {
        val text = button.text.toString()
        val textPaint = button.paint
        val textWidth = textPaint.measureText(text)

        val totalWidth = button.width
        val leftRightPadding = ((totalWidth - textWidth) / 2).toInt().coerceAtLeast(0)

        val originalDrawable = ContextCompat.getDrawable(button.context, R.drawable.layer_tab_background)
        if (originalDrawable is LayerDrawable) {
            val mutableDrawable = originalDrawable.mutate() as LayerDrawable

            // Atur padding kiri dan kanan ke underline
            val underlineIndex = mutableDrawable.findIndexByLayerId(R.id.underline)
            if (underlineIndex >= 0) {
                mutableDrawable.setLayerInset(
                    underlineIndex,
                    leftRightPadding, // kiri
                    0,                // atas
                    leftRightPadding, // kanan
                    0                 // bawah
                )
            }

            button.background = mutableDrawable
        }
    }



    private fun hasPermissions(): Boolean {
        return requiredPermissions.all { permission ->
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, requiredPermissions, PERMISSION_REQUEST_CODE)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                // Izin diberikan, lanjut load musik
                loadMusic()
                preloadAlbumArt()
            } else {
                // Izin ditolak, kasih info ke user
                Toast.makeText(
                    this,
                    "Permission diperlukan untuk menampilkan musik.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    //logika filterMusic
    @SuppressLint("SetTextI18n")
    private fun filterMusic(query: String) {
        val emptyView = findViewById<TextView>(R.id.emptyView)
        val scrollBarContainer = findViewById<View>(R.id.scrollbar_include) // sesuai ID sebenarnya
        val filteredList = if (query.isEmpty()) {
            musicList
        } else {
            musicList.filter { music ->
                music.title.contains(query, ignoreCase = true) ||
                        music.artist.contains(query, ignoreCase = true)
            }
        }

        // Tampilkan atau sembunyikan recyclerView dan scrollbar
        if (filteredList.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyView.visibility = View.VISIBLE
            scrollBarContainer.visibility = View.GONE // sembunyikan scroll bar
            textLabel.text = "0 Songs"
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyView.visibility = View.GONE

            // Sembunyikan scroll bar jika sedang dalam mode pencarian
            scrollBarContainer.visibility = if (query.isEmpty()) View.VISIBLE else View.GONE
            textLabel.text = "${filteredList.size} Songs"
        }


        recyclerView.adapter = MusicAdapter(filteredList, contentResolver)
    }







    @SuppressLint("ClickableViewAccessibility", "SetTextI18n")
    private fun loadMusic() {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        val query = contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection, selection, null, sortOrder
        )

        musicList.clear()

        query?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val music = MusicData(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol),
                    artist = cursor.getString(artistCol),
                    duration = cursor.getLong(durationCol),
                    albumId = cursor.getLong(albumIdCol)
                )
                musicList.add(music)
            }
        }

        recyclerView.adapter = MusicAdapter(musicList, contentResolver)

        // === SCROLLBAR OTOMATIS BERGERAK SAAT SCROLL LIST ===
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val offset = recyclerView.computeVerticalScrollOffset()
                val extent = recyclerView.computeVerticalScrollExtent()
                val range = recyclerView.computeVerticalScrollRange()

                val proportion = if (range - extent > 0) {
                    offset.toFloat() / (range - extent)
                } else {
                    0f
                }

                val containerHeight =
                    findViewById<FrameLayout>(R.id.customScrollBarContainer).height
                val scrollBar = findViewById<View>(R.id.customScrollBar)
                val scrollBarHeight = scrollBar.height

                val translationY = (containerHeight - scrollBarHeight) * proportion
                scrollBar.translationY = translationY

            }
        })

        // === SCROLLBAR BISA DI SENTUH DAN DIGESER ===
        val scrollBar = findViewById<View>(R.id.customScrollBar)
        val container = findViewById<FrameLayout>(R.id.customScrollBarContainer)

        container.setOnTouchListener(object : View.OnTouchListener {
            var initialY = 0f
            var initialScrollY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        val y = event.y
                        val maxScrollY = container.height - scrollBar.height
                        val newScrollY =
                            (y - scrollBar.height / 2).coerceIn(0f, maxScrollY.toFloat())
                        scrollBar.translationY = newScrollY

                        val proportion = newScrollY / maxScrollY
                        val range = recyclerView.computeVerticalScrollRange()
                        val extent = recyclerView.computeVerticalScrollExtent()
                        val targetOffset = (proportion * (range - extent)).toInt()
                        recyclerView.scrollBy(
                            0,
                            targetOffset - recyclerView.computeVerticalScrollOffset()
                        )

                        initialY = event.rawY
                        initialScrollY = newScrollY

                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaY = event.rawY - initialY
                        val maxScrollY = container.height - scrollBar.height
                        val newScrollY =
                            (initialScrollY + deltaY).coerceIn(0f, maxScrollY.toFloat())
                        scrollBar.translationY = newScrollY

                        val proportion = newScrollY / maxScrollY

                        val range = recyclerView.computeVerticalScrollRange()
                        val extent = recyclerView.computeVerticalScrollExtent()
                        val offset = recyclerView.computeVerticalScrollOffset()

                        val targetOffset = (proportion * (range - extent)).toInt()
                        val deltaOffset = targetOffset - offset
                        recyclerView.scrollBy(0, deltaOffset)

                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        return true
                    }
                }
                return false
            }
        })

        // 🔽 Urutkan berdasarkan judul dari A-Z (default)
        musicList.sortBy { it.title.lowercase() }

        // Set adapter dan jumlah lagu
        musicAdapter = MusicAdapter(musicList.toMutableList(), contentResolver)
        recyclerView.adapter = musicAdapter
        textLabel.text = "Total Songs : ${musicList.size}"
    }

    private fun preloadAlbumArt() {
        val albumArtUri = "content://media/external/audio/albumart".toUri()
        val preloadCount = minOf(30, musicList.size)

        for (i in 0 until preloadCount) {
            val music = musicList[i]
            val imageUri = ContentUris.withAppendedId(albumArtUri, music.albumId)
            Glide.with(this).load(imageUri).preload()
        }
    }
}


class MusicAdapter(
    private val musicList: List<MusicData>,
    private val contentResolver: ContentResolver,
    private val onItemClick: (MusicData) -> Unit // Tambahkan ini!
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {



    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    inner class HeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val buttonPlay: MaterialButton = itemView.findViewById(R.id.buttonPlay)
        val buttonRandom: MaterialButton = itemView.findViewById(R.id.buttonRandom)
    }

    inner class MusicViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageViewAlbumArt: ImageView = itemView.findViewById(R.id.imageViewAlbumArt)
        val iconMusic: ImageView = itemView.findViewById(R.id.iconMusic)
        val textTitle: TextView = itemView.findViewById(R.id.textTitle)
        val textArtist: TextView = itemView.findViewById(R.id.textArtist)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION && position > 0) { // karena ada header
                    onItemClick(musicList[position - 1])
                }
            }
        }
    }


    override fun getItemViewType(position: Int): Int {
        return if (position == 0) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_play_random, parent, false)
            HeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_music, parent, false)
            MusicViewHolder(view)
        }
    }

    override fun getItemCount(): Int {
        return musicList.size + 1 // Tambah 1 untuk header
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is MusicViewHolder) {
            val music = musicList[position - 1] // offset karena header
            holder.textTitle.text = music.title
            holder.textArtist.text = music.artist

            val albumArtUri = "content://media/external/audio/albumart".toUri()
            val imageUri = ContentUris.withAppendedId(albumArtUri, music.albumId)

            Glide.with(holder.itemView.context)
                .load(imageUri)
                .placeholder(R.drawable.music_icon2)
                .error(R.drawable.music_icon2)
                .override(100, 100)
                .centerCrop()
                .into(holder.imageViewAlbumArt)

            holder.iconMusic.visibility = View.GONE
        } else if (holder is HeaderViewHolder) {
            holder.buttonPlay.setOnClickListener {
                // aksi putar
            }
            holder.buttonRandom.setOnClickListener {
                // aksi acak
            }
        }
    }
}


class SideBar(private val activity: Activity) {

    val sidebar: LinearLayout = activity.findViewById(R.id.sidebarMenu)
    val buttonMenu: ImageButton = activity.findViewById(R.id.button0)
    val sidebarOverlay: FrameLayout = activity.findViewById(R.id.sidebarOverlay)

    private var downX = 0f

    fun setupGestureAndSwipeOnly() {
        // Gesture saat klik luar sidebar
        sidebarOverlay.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val touchX = event.x
                if (touchX >= sidebar.width) {
                    sidebar.animate().translationX(-sidebar.width.toFloat()).setDuration(300).withEndAction {
                        sidebar.visibility = View.GONE
                        sidebarOverlay.visibility = View.GONE
                    }.start()
                }
                true
            } else {
                true
            }
        }

        // Gesture swipe sidebar
        sidebar.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val moveX = event.rawX
                    val deltaX = moveX - downX
                    val translation = deltaX.coerceIn(-sidebar.width.toFloat(), 0f)
                    sidebar.translationX = translation
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val upX = event.rawX
                    val totalDelta = upX - downX
                    val threshold = 150

                    if (totalDelta < -threshold) {
                        sidebar.animate().translationX(-sidebar.width.toFloat()).setDuration(200).withEndAction {
                            sidebar.visibility = View.GONE
                            sidebarOverlay.visibility = View.GONE
                        }.start()
                    } else {
                        sidebar.animate().translationX(0f).setDuration(200).start()
                    }
                    true
                }
                else -> false
            }
        }
    }
}


