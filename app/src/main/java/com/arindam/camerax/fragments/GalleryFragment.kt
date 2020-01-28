package com.arindam.camerax.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.viewpager.widget.ViewPager
import java.io.File
import android.content.Intent
import android.os.Build
import android.webkit.MimeTypeMap
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.FileProvider
import androidx.appcompat.app.AlertDialog
import com.arindam.camerax.BuildConfig
import com.arindam.camerax.R
import com.arindam.camerax.utils.padWithDisplayCutout
import com.arindam.camerax.utils.showImmersive

const val KEY_ROOT_DIRECTORY = "root_folder"
val EXTENSION_WHITELIST = arrayOf("JPG")

/**
 * Fragment used to present the user with a gallery of photos taken
 *
 * Created by Arindam Karmakar on 9/5/19.
 */
class GalleryFragment internal constructor() : Fragment() {
    private lateinit var rootDirectory: File
    private lateinit var mediaList: MutableList<File>
    private lateinit var mediaViewPager: ViewPager

    /** Adapter class used to present a fragment containing one photo or video as a page */
    inner class MediaPagerAdapter(fm: FragmentManager) : FragmentStatePagerAdapter(fm) {
        override fun getCount(): Int = mediaList.size
        override fun getItem(position: Int): Fragment = PhotoFragment.create(mediaList[position])
        override fun getItemPosition(obj: Any): Int = POSITION_NONE
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_gallery, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Mark this as a retain fragment, so the lifecycle does not get restarted on config change
        retainInstance = true

        arguments?.let {
            rootDirectory = File(it.getString(KEY_ROOT_DIRECTORY))

            // Walk through all files in the root directory
            // We reverse the order of the list to present the last photos first
            mediaList = rootDirectory.listFiles { file ->
                EXTENSION_WHITELIST.contains(file.extension.toUpperCase())
            }.sorted().reversed().toMutableList()

            // Populate the ViewPager and implement a cache of two media items
            mediaViewPager = view.findViewById<ViewPager>(R.id.photo_view_pager).apply {
                offscreenPageLimit = 2
                adapter = MediaPagerAdapter(childFragmentManager)
            }
        }

        // Make sure that the cutout "safe area" avoids the screen notch if any
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            // Use extension method to pad "inside" view containing UI using display cutout's bounds
            view.findViewById<ConstraintLayout>(R.id.cutout_safe_area).padWithDisplayCutout()
        }

        // Handle back button press
        view.findViewById<ImageButton>(R.id.back_button).setOnClickListener {
            fragmentManager?.popBackStack()
        }

        // Handle share button press
        view.findViewById<ImageButton>(R.id.share_button).setOnClickListener {
            // Make sure that we have a file to share
            mediaList.getOrNull(mediaViewPager.currentItem)?.let { mediaFile ->
                val appContext = requireContext().applicationContext

                // Create a sharing intent
                val intent = Intent().apply {
                    // Infer media type from file extension
                    val mediaType = MimeTypeMap.getSingleton()
                        .getMimeTypeFromExtension(mediaFile.extension)
                    // Get URI from our FileProvider implementation
                    val uri = FileProvider.getUriForFile(
                        appContext, BuildConfig.APPLICATION_ID + ".provider", mediaFile
                    )
                    // Set the appropriate intent extra, type, action and flags
                    putExtra(Intent.EXTRA_STREAM, uri)
                    type = mediaType
                    action = Intent.ACTION_SEND
                    flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                }

                // Launch the intent letting the user choose which app to share with
                startActivity(Intent.createChooser(intent, getString(R.string.share_hint)))
            }
        }

        // Handle delete button press
        view.findViewById<ImageButton>(R.id.delete_button).setOnClickListener {
            val context = requireContext()
            AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog)
                .setTitle(getString(R.string.delete_title))
                .setMessage(getString(R.string.delete_dialog))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(android.R.string.yes) { _, _ ->
                    mediaList.getOrNull(mediaViewPager.currentItem)?.let { mediaFile ->

                        // Delete current photo
                        mediaFile.delete()

                        // Notify our view pager
                        mediaList.removeAt(mediaViewPager.currentItem)
                        mediaViewPager.adapter?.notifyDataSetChanged()

                        // If all photos have been deleted, return to camera
                        if (mediaList.isEmpty()) {
                            fragmentManager?.popBackStack()
                        }
                    }
                }

                .setNegativeButton(android.R.string.no, null)
                .create().showImmersive()
        }
    }
}