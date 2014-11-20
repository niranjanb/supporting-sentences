#!/usr/bin/python2.4
# -*- coding: utf-8 -*-
#
# Copyright (C) 2010 Google Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
#
# __author__ = 'jcgregorio@google.com (Joe Gregorio)'
#
"""Simple command-line example for Custom Search."""

import os, sys
import codecs
from apiclient.discovery import build

def main(argv):
    inputFile = argv[0]
    outputFile = argv[1]
    Developer_API_Key = argv[2]
    Custom_Search_Engine_ID = argv[3]

    with open(inputFile) as f:
        queries = f.readlines()

    # Build a service object for interacting with the API. Visit
    # the Google APIs Console <http://code.google.com/apis/console>
    # to get an API key for your own application.
    service = build("customsearch", "v1",
                    developerKey=Developer_API_Key)
    out = codecs.open(outputFile, mode='w', encoding='utf-8')
    for q in queries:
        print "Searching \"%s\"" % (q)
        res = service.cse().list(
            q=q.strip(),
            cx=Custom_Search_Engine_ID,
            start='1'
        ).execute()
        result_id = 1
        for result in res[u'items']:
            print "%s\t%d\t%s\t%s" %(q, result_id, result['link'], result['title'])
            out.write("%s\t%d\t%s\t%s\n" %(q, result_id, result['link'], result['title']))
            result_id += 1
    out.close()

if __name__ == '__main__':
    main(sys.argv[1:])
