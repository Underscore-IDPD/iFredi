package com.ifredi.chat.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.ifredi.chat.R
import com.ifredi.chat.databinding.BottomSheetNewChatBinding
import com.ifredi.chat.ui.adapter.UserSearchAdapter
import com.ifredi.chat.ui.viewmodel.MainViewModel

class NewChatBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetNewChatBinding? = null
    private val binding get() = _binding!!

    // Comparte el ViewModel con MainActivity
    private val viewModel by lazy { ViewModelProvider(requireActivity())[MainViewModel::class.java] }

    private val searchRunnable = Runnable { viewModel.search(binding.etSearchUsers.text.toString()) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetNewChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val adapter = UserSearchAdapter { user -> viewModel.startChatWith(user) }
        binding.rvUsers.layoutManager = LinearLayoutManager(requireContext())
        binding.rvUsers.adapter = adapter

        binding.etSearchUsers.doAfterTextChanged { text ->
            binding.etSearchUsers.removeCallbacks(searchRunnable)
            if (text.isNullOrBlank()) {
                viewModel.search("")
            } else {
                binding.etSearchUsers.postDelayed(searchRunnable, 300)
            }
            refreshEmpty(adapter.itemCount)
        }

        viewModel.searchResults.observe(viewLifecycleOwner) { users ->
            adapter.submitList(users) { refreshEmpty(users.size) }
        }

        binding.etSearchUsers.requestFocus()
    }

    private fun refreshEmpty(count: Int) {
        val querying = !binding.etSearchUsers.text.isNullOrBlank()
        binding.tvUsersEmpty.isVisible = count == 0
        binding.tvUsersEmpty.setText(
            if (querying) R.string.no_results else R.string.new_chat_prompt
        )
    }

    override fun onDestroyView() {
        binding.etSearchUsers.removeCallbacks(searchRunnable)
        viewModel.search("") // limpia resultados para la próxima vez
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val TAG = "NewChatBottomSheet"
    }
}