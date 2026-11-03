package com.rosebank.pitchpulse

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.rosebank.pitchpulse.databinding.ItemBadgeBinding
import com.rosebank.pitchpulse.databinding.ItemFavouriteBinding
import com.rosebank.pitchpulse.databinding.ItemLeaderboardBinding
import com.rosebank.pitchpulse.databinding.ItemMatchBinding
import com.rosebank.pitchpulse.databinding.ItemNewsBinding
import com.rosebank.pitchpulse.databinding.ItemPredictionBinding
import com.rosebank.pitchpulse.databinding.ItemSearchBinding

class MatchAdapter(
    private val onOpen: (MatchEntity) -> Unit,
    private val onStar: (MatchEntity) -> Unit,
    private val starred: (String) -> Boolean
) : RecyclerView.Adapter<MatchAdapter.Holder>() {
    private val items = mutableListOf<MatchEntity>()

    fun submit(data: List<MatchEntity>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    class Holder(val binding: ItemMatchBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(ItemMatchBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val match = items[position]
        val b = holder.binding
        b.league.text = "${match.league} · ${match.sport}"
        b.clock.text = match.clock
        b.homeName.text = match.home
        b.awayName.text = match.away
        b.homeScore.text = match.homeScore.toString()
        b.awayScore.text = match.awayScore.toString()
        b.homeCrest.text = match.homeCode
        b.awayCrest.text = match.awayCode
        b.homeCrest.background = b.homeCrest.background.mutate().apply {
            setTint(Color.parseColor(match.homeColor))
        }
        b.awayCrest.background = b.awayCrest.background.mutate().apply {
            setTint(Color.parseColor(match.awayColor))
        }
        val dot = when (match.status) {
            "LIVE" -> R.color.live
            "HT" -> R.color.ht
            else -> R.color.muted
        }
        b.statusDot.background.setTint(holder.itemView.context.getColor(dot))
        b.star.setImageResource(if (starred(match.id)) R.drawable.ic_star else R.drawable.ic_star_outline)
        b.star.contentDescription = if (starred(match.id)) "Unstar ${match.home}" else "Star ${match.home} versus ${match.away}"
        b.root.contentDescription = "${match.home} ${match.homeScore}, ${match.away} ${match.awayScore}, ${match.clock}"
        b.root.setOnClickListener { onOpen(match) }
        b.star.setOnClickListener { onStar(match) }
    }
}

class NewsAdapter(private val onOpen: (NewsEntity) -> Unit) :
    RecyclerView.Adapter<NewsAdapter.Holder>() {
    private val items = mutableListOf<NewsEntity>()
    fun submit(data: List<NewsEntity>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemNewsBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemNewsBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.meta.text = "${item.source} · ${item.tag}"
        holder.binding.title.text = item.title
        holder.binding.summary.text = item.summary
        holder.binding.root.setOnClickListener { onOpen(item) }
    }
}

class FavouriteAdapter(
    private val onMove: (FavouriteEntity) -> Unit,
    private val onRemove: (FavouriteEntity) -> Unit
) : RecyclerView.Adapter<FavouriteAdapter.Holder>() {
    private val items = mutableListOf<FavouriteEntity>()
    fun submit(data: List<FavouriteEntity>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemFavouriteBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemFavouriteBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.binding.label.text = item.label
        holder.binding.up.setOnClickListener { onMove(item) }
        holder.binding.remove.setOnClickListener { onRemove(item) }
    }
}

class PredictionAdapter(private val onPredict: (MatchEntity) -> Unit) :
    RecyclerView.Adapter<PredictionAdapter.Holder>() {
    private val items = mutableListOf<MatchEntity>()
    fun submit(data: List<MatchEntity>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemPredictionBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemPredictionBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val match = items[position]
        val pred = Repository.prediction(match.id)
        holder.binding.fixture.text = "${match.home} vs ${match.away}"
        holder.binding.status.text = when {
            pred?.settled == true -> "Settled · ${pred.points} pts"
            pred != null && pred.queued -> "Queued offline · ${pred.resultPick}"
            pred != null -> "Locked · ${pred.resultPick} ${pred.scorePick}"
            match.status == "NS" -> "Open · ${match.clock}"
            else -> match.status
        }
        holder.binding.predict.isEnabled = match.status == "NS" && pred == null
        holder.binding.predict.text = if (pred == null) "Predict" else "Saved"
        holder.binding.predict.setOnClickListener { onPredict(match) }
    }
}

class LeaderboardAdapter : RecyclerView.Adapter<LeaderboardAdapter.Holder>() {
    private val items = mutableListOf<BoardRow>()
    fun submit(data: List<BoardRow>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemLeaderboardBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemLeaderboardBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val row = items[position]
        holder.binding.rank.text = "${position + 1}"
        holder.binding.name.text = if (row.you) "${row.name} (you)" else row.name
        holder.binding.points.text = "${row.points}"
    }
}

class SearchAdapter(
    private val onStar: (SearchHit) -> Unit,
    private val starred: (SearchHit) -> Boolean
) : RecyclerView.Adapter<SearchAdapter.Holder>() {
    private val items = mutableListOf<SearchHit>()
    fun submit(data: List<SearchHit>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemSearchBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemSearchBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val hit = items[position]
        holder.binding.label.text = hit.label
        holder.binding.kind.text = hit.kind
        holder.binding.star.setImageResource(
            if (starred(hit)) R.drawable.ic_star else R.drawable.ic_star_outline
        )
        holder.binding.star.setOnClickListener { onStar(hit) }
    }
}

data class BadgeUi(val name: String, val detail: String, val progress: Int)

class BadgeAdapter : RecyclerView.Adapter<BadgeAdapter.Holder>() {
    private val items = mutableListOf<BadgeUi>()
    fun submit(data: List<BadgeUi>) {
        items.clear(); items.addAll(data); notifyDataSetChanged()
    }
    class Holder(val binding: ItemBadgeBinding) : RecyclerView.ViewHolder(binding.root)
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(ItemBadgeBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    override fun getItemCount() = items.size
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val badge = items[position]
        holder.binding.name.text = badge.name
        holder.binding.detail.text = badge.detail
        holder.binding.progress.progress = badge.progress
        holder.binding.root.alpha = if (badge.progress >= 100) 1f else 0.55f
    }
}
