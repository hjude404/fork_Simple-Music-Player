package com.simplemobiletools.musicplayer.adapters

import android.view.Menu
import android.view.View
import android.view.ViewGroup
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.bumptech.glide.request.RequestOptions
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import com.simplemobiletools.commons.activities.BaseSimpleActivity
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.dialogs.ConfirmationDialog
import com.simplemobiletools.commons.extensions.highlightTextPart
import com.simplemobiletools.commons.extensions.setupViewBackground
import com.simplemobiletools.commons.helpers.ensureBackgroundThread
import com.simplemobiletools.commons.views.MyRecyclerView
import com.simplemobiletools.musicplayer.R
import com.simplemobiletools.musicplayer.extensions.*
import com.simplemobiletools.musicplayer.inlines.indexOfFirstOrNull
import com.simplemobiletools.musicplayer.models.Artist
import com.simplemobiletools.musicplayer.models.Track
import kotlinx.android.synthetic.main.item_artist.view.artist_albums_tracks
import kotlinx.android.synthetic.main.item_artist.view.artist_frame
import kotlinx.android.synthetic.main.item_artist.view.artist_title

class ArtistsAdapter(activity: BaseSimpleActivity, var artists: ArrayList<Artist>, recyclerView: MyRecyclerView, itemClick: (Any) -> Unit) :
    MyRecyclerViewAdapter(activity, recyclerView, itemClick), RecyclerViewFastScroller.OnPopupTextUpdate {

    private var textToHighlight = ""
    private val placeholder = resources.getSmallPlaceholder(textColor)
    private val cornerRadius = resources.getDimension(R.dimen.rounded_corner_radius_small).toInt()

    init {
        setupDragListener(true)
    }

    override fun getActionMenuId() = R.menu.cab_artists

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = createViewHolder(R.layout.item_artist, parent)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = artists.getOrNull(position) ?: return
        holder.bindView(artist, true, true) { itemView, layoutPosition ->
            setupView(itemView, artist)
        }
        bindViewHolder(holder)
    }

    override fun getItemCount() = artists.size

    override fun prepareActionMode(menu: Menu) {}

    override fun actionItemPressed(id: Int) {
        if (selectedKeys.isEmpty()) {
            return
        }

        when (id) {
            R.id.cab_add_to_playlist -> addToPlaylist()
            R.id.cab_add_to_queue -> addToQueue()
            R.id.cab_delete -> askConfirmDelete()
            R.id.cab_share -> shareFiles()
            R.id.cab_select_all -> selectAll()
        }
    }

    override fun getSelectableItemCount() = artists.size

    override fun getIsItemSelectable(position: Int) = true

    override fun getItemSelectionKey(position: Int) = artists.getOrNull(position)?.hashCode()

    override fun getItemKeyPosition(key: Int) = artists.indexOfFirst { it.hashCode() == key }

    override fun onActionModeCreated() {}

    override fun onActionModeDestroyed() {}

    private fun addToPlaylist() {
        ensureBackgroundThread {
            val allSelectedTracks = getAllSelectedTracks()
            activity.runOnUiThread {
                activity.addTracksToPlaylist(allSelectedTracks) {
                    finishActMode()
                    notifyDataSetChanged()
                }
            }
        }
    }

    private fun addToQueue() {
        ensureBackgroundThread {
            activity.addTracksToQueue(getAllSelectedTracks()) {
                finishActMode()
            }
        }
    }

    private fun getAllSelectedTracks(): ArrayList<Track> {
        val albums = activity.audioHelper.getArtistAlbums(getSelectedArtists())
        return activity.audioHelper.getAlbumTracks(albums)
    }

    private fun askConfirmDelete() {
        ConfirmationDialog(activity) {
            ensureBackgroundThread {
                val selectedArtists = getSelectedArtists()
                val positions = selectedArtists.mapNotNull { artist -> artists.indexOfFirstOrNull { it.id == artist.id } } as ArrayList<Int>
                val tracks = activity.audioHelper.getArtistTracks(selectedArtists)

                activity.audioHelper.deleteArtists(selectedArtists)
                activity.deleteTracks(tracks) {
                    activity.runOnUiThread {
                        positions.sortDescending()
                        removeSelectedItems(positions)
                        positions.forEach {
                            if (artists.size > it) {
                                artists.removeAt(it)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shareFiles() {
        ensureBackgroundThread {
            activity.shareTracks(getAllSelectedTracks())
        }
    }

    private fun getSelectedArtists(): List<Artist> = artists.filter { selectedKeys.contains(it.hashCode()) }.toList()

    fun updateItems(newItems: ArrayList<Artist>, highlightText: String = "", forceUpdate: Boolean = false) {
        if (forceUpdate || newItems.hashCode() != artists.hashCode()) {
            artists = newItems.clone() as ArrayList<Artist>
            textToHighlight = highlightText
            notifyDataSetChanged()
            finishActMode()
        } else if (textToHighlight != highlightText) {
            textToHighlight = highlightText
            notifyDataSetChanged()
        }
    }

    private fun setupView(view: View, artist: Artist) {
        view.apply {
            setupViewBackground(activity)
            artist_frame?.isSelected = selectedKeys.contains(artist.hashCode())
            artist_title.text = if (textToHighlight.isEmpty()) artist.title else artist.title.highlightTextPart(textToHighlight, properPrimaryColor)
            artist_title.setTextColor(textColor)

            val albums = resources.getQuantityString(R.plurals.albums_plural, artist.albumCnt, artist.albumCnt)
            val tracks = resources.getQuantityString(R.plurals.tracks_plural, artist.trackCnt, artist.trackCnt)
            artist_albums_tracks.text = "$albums, $tracks"
            artist_albums_tracks.setTextColor(textColor)

            activity.getArtistCoverArt(artist) { coverArt ->
                val options = RequestOptions()
                    .error(placeholder)
                    .transform(CenterCrop(), RoundedCorners(cornerRadius))

                activity.ensureActivityNotDestroyed {
                    Glide.with(activity)
                        .load(coverArt)
                        .apply(options)
                        .into(findViewById(R.id.artist_image))
                }
            }
        }
    }

    override fun onChange(position: Int) = artists.getOrNull(position)?.getBubbleText(activity.config.artistSorting) ?: ""
}
