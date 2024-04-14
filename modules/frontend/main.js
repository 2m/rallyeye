import './style/main.css'
import './style/tooltip.css'
import '@linkOutputDir/main.js'
import 'flowbite';
import { polyfillCountryFlagEmojis } from "country-flag-emoji-polyfill";

// Chrome does not come with country flag emojis installed.
// Windows does not have them installed either.
// Use this polyfill to enable flag emojis on Windows Chrome.
// https://github.com/talkjs/country-flag-emoji-polyfill
polyfillCountryFlagEmojis();
