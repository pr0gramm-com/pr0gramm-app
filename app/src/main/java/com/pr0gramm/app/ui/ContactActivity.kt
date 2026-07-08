package com.pr0gramm.app.ui

import android.app.Activity
import android.os.Bundle
import android.util.Patterns
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.pr0gramm.app.R
import com.pr0gramm.app.services.ContactService
import com.pr0gramm.app.services.ThemeHelper
import com.pr0gramm.app.services.UserService
import com.pr0gramm.app.ui.base.BaseAppCompatActivity
import com.pr0gramm.app.ui.base.launchWhenStarted
import com.pr0gramm.app.ui.compose.setComposeContent
import com.pr0gramm.app.util.di.instance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Compose contact / feedback form. Sends feedback via [ContactService].
 */
class ContactActivity : BaseAppCompatActivity("ContactActivity") {
    private val contactService: ContactService by instance()
    private val userService: UserService by instance()

    private var submitting by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(ThemeHelper.theme.basic)
        super.onCreate(savedInstanceState)

        val loggedIn = userService.isAuthorized

        setComposeContent {
            ContactScreen(
                loggedIn = loggedIn,
                submitting = submitting,
                onBack = { finish() },
                onSubmit = ::submitClicked,
            )
        }
    }

    private fun submitClicked(category: Category, email: String, subject: String, feedback: String) {
        launchWhenStarted(busyIndicator = true) {
            submitting = true
            try {
                sendFeedback(category, email, subject, feedback)
                onSubmitSuccess()
            } finally {
                submitting = false
            }
        }
    }

    private suspend fun sendFeedback(category: Category, email: String, subject: String, feedback: String) {
        var finalSubject = subject.trim()
        if (category.category == "app") {
            finalSubject = "[app] $finalSubject"
        }

        withContext(Dispatchers.IO + NonCancellable) {
            contactService.post(email.trim(), finalSubject, feedback.trim())
        }
    }

    private fun onSubmitSuccess() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}

private class Category(val category: String, val text: String)

private val faqCategories = listOf(
    Category("app", "App"),
    Category("pr0gramm", "pr0gramm"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactScreen(
    loggedIn: Boolean,
    submitting: Boolean,
    onBack: () -> Unit,
    onSubmit: (category: Category, email: String, subject: String, feedback: String) -> Unit,
) {
    var category by rememberSaveable(stateSaver = categorySaver) { mutableStateOf<Category?>(null) }
    var email by rememberSaveable { mutableStateOf("") }
    var subject by rememberSaveable { mutableStateOf("") }
    var feedback by rememberSaveable { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }

    val emailValid = !loggedIn && Patterns.EMAIL_ADDRESS.matcher(email).matches()

    val canSubmit = !submitting &&
            category != null &&
            subject.isNotBlank() &&
            feedback.isNotBlank() &&
            (loggedIn || (email.isNotBlank() && emailValid))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = it },
            ) {
                OutlinedTextField(
                    value = category?.text ?: "Kategorie auswählen",
                    onValueChange = {},
                    readOnly = true,
                    enabled = !submitting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded)
                    },
                )

                ExposedDropdownMenu(
                    expanded = categoryExpanded,
                    onDismissRequest = { categoryExpanded = false },
                ) {
                    for (item in faqCategories) {
                        DropdownMenuItem(
                            text = { Text(item.text) },
                            onClick = {
                                category = item
                                categoryExpanded = false
                            },
                        )
                    }
                }
            }

            if (!loggedIn) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !submitting,
                    singleLine = true,
                    isError = email.isNotBlank() && !emailValid,
                    label = { Text(stringResource(R.string.feedback_email)) },
                )
            }

            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                singleLine = true,
                label = { Text(stringResource(R.string.feedback_subject)) },
            )

            OutlinedTextField(
                value = feedback,
                onValueChange = { feedback = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !submitting,
                minLines = 5,
                label = { Text(stringResource(R.string.feedback_feedback)) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )

            DeletionHint()

            Button(
                onClick = {
                    val selected = category ?: return@Button
                    onSubmit(selected, email, subject, feedback)
                },
                enabled = canSubmit,
                modifier = Modifier.align(androidx.compose.ui.Alignment.End),
            ) {
                Text(stringResource(R.string.feedback_submit))
            }
        }
    }
}

@Composable
private fun DeletionHint() {
    val hint = stringResource(R.string.feedback_delete_post_hint)
    val accent = MaterialTheme.colorScheme.secondary

    val annotated = remember(hint, accent) {
        val url = "https://pr0gramm.com/faq:delete"
        val idx = hint.indexOf(url)
        buildAnnotatedString {
            if (idx < 0) {
                append(hint)
            } else {
                append(hint.substring(0, idx))
                withLink(
                    LinkAnnotation.Url(
                        url,
                        TextLinkStyles(
                            SpanStyle(color = accent, textDecoration = TextDecoration.Underline),
                        ),
                    ),
                ) {
                    append(url)
                }
                append(hint.substring(idx + url.length))
            }
        }
    }

    Text(annotated, style = MaterialTheme.typography.bodySmall)
}

private val categorySaver = androidx.compose.runtime.saveable.Saver<Category?, String>(
    save = { it?.category ?: "" },
    restore = { key -> faqCategories.firstOrNull { it.category == key } },
)
