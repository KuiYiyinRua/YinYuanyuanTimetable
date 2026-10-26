package com.yinyuanyuan.timetable

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object AuthorLinks {
    const val STEAM="https://store.steampowered.com/app/4893790/%E4%B8%8E%E4%B8%AD%E8%8D%89%E8%8D%AF%E5%A8%98%E7%9A%84%E6%97%A5%E5%B8%B8/"
    const val BILIBILI="https://space.bilibili.com/3546821542545653"

    fun open(context: Context, url: String) {
        try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(context,"未找到可打开链接的应用",Toast.LENGTH_SHORT).show() }
    }
}

@Composable fun AuthorSupport() {
    val context=LocalContext.current
    Column(Modifier.fillMaxWidth().padding(top=24.dp)) {
        HorizontalDivider(color=Rule)
        Text("支持作者",fontSize=12.sp,color=Muted,modifier=Modifier.padding(top=18.dp,bottom=12.dp))
        Row(Modifier.fillMaxWidth().clickable {AuthorLinks.open(context,AuthorLinks.STEAM)}
            .semantics {contentDescription="在 Steam 关注与中草药娘的日常"},verticalAlignment=Alignment.CenterVertically) {
            Image(painterResource(R.drawable.herb_girl_promo),contentDescription="与中草药娘的日常宣传图",
                modifier=Modifier.width(112.dp).aspectRatio(1232f/706f).clip(RoundedCornerShape(6.dp)),contentScale=ContentScale.Fit)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("与中草药娘的日常",fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium)
                Text("如果希望支持作者，可以去 Steam 关注作者的游戏。",fontSize=12.sp,lineHeight=18.sp,color=Muted,modifier=Modifier.padding(top=4.dp))
            }
        }
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable {AuthorLinks.open(context,AuthorLinks.BILIBILI)}
            .semantics {contentDescription="作者尹怨怨的 B 站主页"},verticalAlignment=Alignment.CenterVertically) {
            Text("作者 · 尹怨怨",fontSize=13.sp,modifier=Modifier.weight(1f))
            Text("B 站主页 ↗",fontSize=12.sp,color=Muted)
        }
    }
}
