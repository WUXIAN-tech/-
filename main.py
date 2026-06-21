"""Kivy app for extracting MP3 audio from MP4 videos."""

import os
import threading

from kivy.app import App
from kivy.uix.boxlayout import BoxLayout
from kivy.uix.label import Label
from kivy.uix.button import Button
from kivy.uix.progressbar import ProgressBar
from kivy.uix.popup import Popup
from kivy.clock import Clock
from kivy.utils import platform

from converter import extract_audio


class AudioExtractorUI(BoxLayout):
    """Main UI layout."""

    def __init__(self, **kwargs):
        super().__init__(orientation='vertical', padding=30, spacing=20, **kwargs)

        # Title
        self.add_widget(Label(
            text='MP4 → MP3 音频提取器',
            font_size='22sp',
            size_hint=(1, 0.15),
            bold=True,
            color=(0.2, 0.6, 1, 1)
        ))

        # File picker button
        self.pick_btn = Button(
            text='选择 MP4 文件',
            font_size='18sp',
            size_hint=(1, 0.15),
            background_color=(0.2, 0.6, 1, 1)
        )
        self.pick_btn.bind(on_press=self._pick_file)
        self.add_widget(self.pick_btn)

        # Selected file label
        self.file_label = Label(
            text='未选择文件',
            font_size='14sp',
            size_hint=(1, 0.1),
            color=(0.5, 0.5, 0.5, 1)
        )
        self.add_widget(self.file_label)

        # Convert button
        self.convert_btn = Button(
            text='开始转换',
            font_size='20sp',
            size_hint=(1, 0.18),
            background_color=(0.2, 0.8, 0.3, 1),
            disabled=True
        )
        self.convert_btn.bind(on_press=self._start_convert)
        self.add_widget(self.convert_btn)

        # Progress bar
        self.progress = ProgressBar(max=100, value=0, size_hint=(1, 0.08))
        self.add_widget(self.progress)

        # Status label
        self.status_label = Label(
            text='',
            font_size='14sp',
            size_hint=(1, 0.15),
            color=(0.8, 0.8, 0.8, 1)
        )
        self.add_widget(self.status_label)

        # Internal state
        self._selected_file = None

    def _pick_file(self, instance):
        if platform == 'android':
            from plyer import filechooser
            filechooser.open_file(
                on_selection=self._on_file_selected,
                filters=['*.mp4', '*.MP4']
            )
        else:
            # Desktop fallback
            from plyer import filechooser
            filechooser.open_file(
                on_selection=self._on_file_selected,
                filters=['*.mp4', '*.MP4']
            )

    def _on_file_selected(self, selection):
        if not selection:
            return
        self._selected_file = selection[0]
        self.file_label.text = f'已选择: {os.path.basename(self._selected_file)}'
        self.convert_btn.disabled = False
        self.status_label.text = ''

    def _start_convert(self, instance):
        if not self._selected_file:
            return

        self.convert_btn.disabled = True
        self.pick_btn.disabled = True
        self.progress.value = 0
        self.status_label.text = '正在转换中...'

        thread = threading.Thread(target=self._run_conversion, daemon=True)
        thread.start()

    def _run_conversion(self):
        try:
            def on_progress(duration, current):
                pct = int(current / duration * 100) if duration > 0 else 0
                Clock.schedule_once(lambda dt, v=pct: setattr(self.progress, 'value', v))
                Clock.schedule_once(
                    lambda dt, d=duration, c=current: setattr(
                        self.status_label, 'text',
                        f'转换中... {c:.0f}s / {d:.0f}s'
                    )
                )

            output = extract_audio(
                self._selected_file,
                progress_callback=on_progress
            )
            Clock.schedule_once(
                lambda dt, p=output: self._on_success(p)
            )
        except Exception as e:
            Clock.schedule_once(
                lambda dt, msg=str(e): self._on_error(msg)
            )

    def _on_success(self, output_path):
        self.progress.value = 100
        self.status_label.text = f'✓ 转换完成！\n保存至: {output_path}'
        self.convert_btn.disabled = False
        self.pick_btn.disabled = False
        self._show_popup('转换成功', f'文件已保存:\n{output_path}')

    def _on_error(self, message):
        self.status_label.text = f'✗ 转换失败: {message}'
        self.progress.value = 0
        self.convert_btn.disabled = False
        self.pick_btn.disabled = False
        self._show_popup('转换失败', message)

    def _show_popup(self, title, content):
        popup = Popup(
            title=title,
            content=Label(text=content, font_size='14sp'),
            size_hint=(0.8, 0.4)
        )
        popup.open()


class AudioExtractorApp(App):
    def build(self):
        return AudioExtractorUI()


if __name__ == '__main__':
    AudioExtractorApp().run()
